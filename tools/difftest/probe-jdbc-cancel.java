import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * B-1 机制的最小独立复现 —— 不经过 HTTP / gRPC / MyBatis，
 * 只测「一条阻塞在 PG 上的 JDBC 查询，到底能被什么机制打断」。
 *
 * <p><b>为什么要单独做这个（它补的是端到端实验补不了的证据）</b>
 * <p>端到端实验（{@code exp-b1-timeout-cancel.sh}）里「客户端返回」「PG 查询消失」
 * 「worker 线程退出」是三件不同的事，混在一起量会读不出机制 ——
 * 2026-10-21 就出现过「客户端 2121ms 返回 / 查询 398ms 消失 / worker 却 6860ms 后才结束」
 * 的三方矛盾，单看任何一个数都得不出结论。这里把每个机制单独测。
 *
 * <p><b>实测结论（2026-10-21，pgjdbc 42.6.2，PostgreSQL 16-alpine，锁持 10s）</b>
 * <pre>
 *   mode        结果                                                    耗时
 *   ─────────────────────────────────────────────────────────────────────────
 *   none        ✅ 正常返回（阻塞到锁释放）                            10022ms
 *   qto:2       ❌ SQLState=57014 canceling statement due to user request  2034ms
 *   interrupt   ✅ 正常返回 —— interrupt 对 JDBC 查询【完全无效】          11014ms
 * </pre>
 *
 * <p>⇒ 两条结论：
 * <ol>
 *   <li><b>B-1 坐实且升级为运行时证据</b>：此前只有字节码证据
 *       （MyBatis {@code BaseExecutor}/{@code SimpleExecutor} 0 次、
 *        pgjdbc {@code QueryExecutorImpl} 0 次 {@code isInterrupted}）与端到端的
 *       「查询多存活 4.4s」。现在有直接行为证据：主动 interrupt 之后，
 *       查询照跑不误、耗时甚至比不 interrupt 还长 1s（interrupt 本身没带来任何等待）。</li>
 *   <li><b>{@code setQueryTimeout} 是可行的替代机制</b>：它让 pgjdbc 起
 *       {@code StatementCancelTimerTask} → 到点发 <b>CancelRequest</b> → PG 停止查询，
 *       连接保持可用（<b>不是</b> socketTimeout 那种「连接报废」语义）。
 *       异常耗时 2034ms ≈ 设定值 2s + 34ms 开销，误差可接受。</li>
 * </ol>
 *
 * <p><b>与 Go 的语义等价性</b>：Go 的 {@code context.WithTimeout} 到点后
 * database/sql 通过 ctx 取消把查询停掉，<b>连接回到池里可用</b>；
 * {@code setQueryTimeout} 走的正是同一条服务端语义（CancelRequest），
 * 且同样保连接。⇒ 机制层面等价。
 *
 * <p><b>用法</b>（需要 difftest-postgres 在 55432 上；本类会自己建表）：
 * <pre>
 *   cd tools/difftest
 *   java -cp ~/.m2/repository/org/postgresql/postgresql/42.6.2/postgresql-42.6.2.jar \
 *        probe-jdbc-cancel.java none
 *   java -cp .../postgresql-42.6.2.jar probe-jdbc-cancel.java qto:2
 *   java -cp .../postgresql-42.6.2.jar probe-jdbc-cancel.java interrupt
 * </pre>
 * 三组各约 10s。类名与文件名不一致是为了让它在 tools/difftest/ 里语义清晰，
 * JEP 330 单文件源码模式不要求匹配。
 */
public class ProbeJdbcCancel {

    static final String URL = "jdbc:postgresql://127.0.0.1:55432/rag_db";
    // ⚠️ 密码是 difftest，不是 admin —— 跟 docker-compose.yml 的 POSTGRES_PASSWORD 一致。
    //    写 admin 会报 "password authentication failed"：docker exec 走容器内 trust，
    //    而这里是 TCP 连接，必须走 scram/md5 认证。踩过一次。
    static final String USER = "admin", PASS = "difftest";
    static final String TABLE = "users_user";
    static final int LOCK_SECONDS = 10;

    static volatile long t0;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "none";
        ensureTable();
        System.out.printf("%n========== mode=%s（锁持 %ds）==========%n", mode, LOCK_SECONDS);

        // 连接 1：拿 ACCESS EXCLUSIVE 表锁。
        // ⚠️ 必须是【表级 ACCESS EXCLUSIVE】，不能用 SELECT ... FOR UPDATE 的行锁 ——
        //    PostgreSQL 普通 SELECT 走 MVCC 快照读，不会被 FOR UPDATE 持有的行锁挡住。
        //    用行锁的话查询会直接成功返回，三组全是「✅ 正常返回」，实验毫无区分度。
        Connection lockConn = DriverManager.getConnection(URL, USER, PASS);
        lockConn.setAutoCommit(false);
        try (Statement s = lockConn.createStatement()) {
            s.execute("LOCK TABLE " + TABLE + " IN ACCESS EXCLUSIVE MODE");
        }
        System.out.println("[lock ] ACCESS EXCLUSIVE 已持有，" + LOCK_SECONDS + "s 后释放");

        // 连接 2：跑会被挡住的查询。放独立线程，主线程负责到点放锁。
        Thread worker = new Thread(() -> {
            t0 = System.nanoTime();
            try (Connection q = DriverManager.getConnection(URL, USER, PASS);
                 Statement s = q.createStatement()) {
                if (mode.startsWith("qto:")) {
                    int sec = Integer.parseInt(mode.substring(4));
                    s.setQueryTimeout(sec);
                    System.out.println("[query] 已调用 setQueryTimeout(" + sec + ")");
                }
                try (ResultSet rs = s.executeQuery(
                        "SELECT count(*) FROM " + TABLE + " WHERE username='jdbc_probe'")) {
                    rs.next();
                    System.out.println("[query] ✅ 正常返回 count=" + rs.getInt(1)
                            + "，耗时 " + ms() + "ms");
                }
            } catch (SQLException e) {
                System.out.println("[query] ❌ 抛 SQL 异常，耗时 " + ms() + "ms");
                System.out.println("[query]    SQLState = " + e.getSQLState());
                System.out.println("[query]    class    = " + e.getClass().getName());
                System.out.println("[query]    message  = " + e.getMessage());
                for (Throwable c = e.getCause(); c != null; c = c.getCause()) {
                    System.out.println("[query]    caused by: " + c.getClass().getName()
                            + " : " + c.getMessage());
                }
            }
        }, "probe-worker");
        worker.setDaemon(true);
        worker.start();

        // interrupt 组：1s 后打断 worker 线程。
        // 关键对照：B-1 声称「JDBC 不响应线程 interrupt」，这里就是它的行为级验证。
        if (mode.equals("interrupt")) {
            Thread.sleep(1000);
            worker.interrupt();
            System.out.println("[main ] 已在 1000ms 处调用 worker.interrupt()");
        }

        Thread.sleep(LOCK_SECONDS * 1000L);
        lockConn.rollback();
        lockConn.close();
        System.out.println("[lock ] 锁已释放");
        worker.join(5000);
        System.exit(0);
    }

    /** 幂等建表 —— 让本探针能脱离两套栈单独跑。 */
    static void ensureTable() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS " + TABLE
                    + " (id bigserial PRIMARY KEY, username varchar(64))");
        }
    }

    static long ms() { return (System.nanoTime() - t0) / 1_000_000; }
}
