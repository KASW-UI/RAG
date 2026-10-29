// users-common 双侧活体对拍探针 —— **Go 真源侧 · admin 模型**。
//
// ⚠️ 与 `main.go` 同理：**永不写进真源仓**。`run.sh` 用 `go build -overlay=<json>`
// 把它虚拟挂进真源模块树的
// `service/user/admin/rpc/cmd/userscommonprobe/main.go`。
//
// 为什么必须单独一个程序：Go 的 `internal` 规则按**导入路径**判定，
// `service/user/admin/rpc/internal/model` 只能被 `service/user/admin/rpc/...` 下的包 import
// ⇒ `main.go`（挂在 user/rpc 下）**看不到** admin 的 model。
//
// 真源（**只读**）：
//   service/user/admin/rpc/internal/model/db.go:15  db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})
//   service/user/admin/rpc/internal/model/data_model.go —— Admin / AdminInvite 的 struct 定义
package main

import (
	"bufio"
	"fmt"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"gorm.io/gorm"

	"sea-try-go/service/user/admin/rpc/internal/model"
)

const (
	fixedBcryptHash = "$2a$10$k5Q0/H16AzxsOsbbng9Mz.S0S21mTo6/xYlKwLm.9wUw9UGJPoBMm"
	seedAdminUid    = int64(9007199254740994)
	seedInviteCode  = "ADM-PROBE0001"
)

type emitter struct {
	w *bufio.Writer
}

func (e *emitter) emit(id, value string) {
	v := strings.ReplaceAll(value, "\\", "\\\\")
	v = strings.ReplaceAll(v, "\t", "\\t")
	v = strings.ReplaceAll(v, "\n", "\\n")
	v = strings.ReplaceAll(v, "\r", "\\r")
	fmt.Fprintf(e.w, "%s\t%s\n", id, v)
}

func main() {
	outPath := flagOr("--out=", "")
	pgHost := flagOr("--pg-host=", "127.0.0.1")
	pgPort := flagOr("--pg-port=", "5432")
	pgUser := flagOr("--pg-user=", "admin")
	pgPass := flagOr("--pg-pass=", "sea")
	pgDb := flagOr("--pg-db=", "first_db")
	if outPath == "" {
		fmt.Fprintln(os.Stderr, "usage: probe-admin --out=<file> [--pg-*]")
		os.Exit(2)
	}

	f, err := os.Create(outPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "cannot create %s: %v\n", outPath, err)
		os.Exit(2)
	}
	em := &emitter{w: bufio.NewWriter(f)}
	defer em.w.Flush()

	// admin 侧的 DDL 真源路径：**admin-rpc 自己的 InitDB**（dsn 形态）。
	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai",
		pgHost, pgUser, pgPass, pgDb, pgPort, "disable")
	db := model.InitDB(dsn)

	email := "probe-admin@example.invalid"
	admin := model.Admin{
		Uid:       seedAdminUid,
		Username:  "probe-admin",
		Password:  fixedBcryptHash,
		Email:     &email,
		ExtraInfo: map[string]string{"a": "1", "b": "2"},
	}
	if err := db.Create(&admin).Error; err != nil {
		em.emit("c.seed.admin_admin.error", "ERR:"+err.Error())
		return
	}
	expires := time.Date(2026, 1, 2, 3, 4, 5, 0, time.UTC)
	invite := model.AdminInvite{
		Code:       seedInviteCode,
		InviterUid: seedAdminUid,
		ExpiresAt:  expires,
	}
	if err := db.Create(&invite).Error; err != nil {
		em.emit("c.seed.admin_invite.error", "ERR:"+err.Error())
		return
	}

	var got model.Admin
	if err := db.Where("uid = ?", seedAdminUid).First(&got).Error; err != nil {
		em.emit("c.seed.admin_admin.error", "ERR:"+err.Error())
		return
	}
	em.emit("c.seed.admin_admin.uid", strconv.FormatInt(got.Uid, 10))
	em.emit("c.seed.admin_admin.username", got.Username)
	em.emit("c.seed.admin_admin.password", got.Password)
	gotEmail := ""
	if got.Email != nil {
		gotEmail = *got.Email
	}
	em.emit("c.seed.admin_admin.email", gotEmail)
	em.emit("c.seed.admin_admin.extra_info", canonicalJSON(map[string]any{
		"a": got.ExtraInfo["a"],
		"b": got.ExtraInfo["b"],
	}))

	var gotInvite model.AdminInvite
	if err := db.Where("code = ?", seedInviteCode).First(&gotInvite).Error; err != nil {
		em.emit("c.seed.admin_invite.error", "ERR:"+err.Error())
		return
	}
	em.emit("c.seed.admin_invite.code", gotInvite.Code)
	em.emit("c.seed.admin_invite.inviter_uid", strconv.FormatInt(gotInvite.InviterUid, 10))
	em.emit("c.seed.admin_invite.expires_at_epoch", strconv.FormatInt(gotInvite.ExpiresAt.Unix(), 10))
	usedBy := ""
	if gotInvite.UsedByUid != nil {
		usedBy = strconv.FormatInt(*gotInvite.UsedByUid, 10)
	}
	em.emit("c.seed.admin_invite.used_by_uid", usedBy)
	usedAt := ""
	if gotInvite.UsedAt != nil {
		usedAt = strconv.FormatInt(gotInvite.UsedAt.Unix(), 10)
	}
	em.emit("c.seed.admin_invite.used_at_epoch", usedAt)

	emitColumns(em, db, "admins", "admin_admin")
	emitColumns(em, db, "admin_invites", "admin_invite")

	// 表清单（**admin 探针最后跑** ⇒ 四张表都在库里）。
	// ⚠️ 两侧的**表名不同**（Go: users/admins/admin_invites/user_avatar_history；
	//    RAG: users_user/admin_admin/admin_invite/users_user_avatar_history）⇒
	//    这是**已登记的命名约定**（台账 A8 ④「表名 users→users_user ✅ 签字」）
	//    ⇒ 登记为 REG 行，不参与「必须相同」判定。
	var tables []string
	if err := db.Raw("SELECT table_name FROM information_schema.tables "+
		"WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' "+
		"ORDER BY table_name").Scan(&tables).Error; err != nil {
		em.emit("c.ddl.tables", "ERR:"+err.Error())
		return
	}
	em.emit("c.ddl.tables", strings.Join(tables, ","))
}

func canonicalJSON(obj map[string]any) string {
	keys := []string{"a", "b"}
	var b strings.Builder
	b.WriteByte('{')
	for i, k := range keys {
		if i > 0 {
			b.WriteByte(',')
		}
		b.WriteString(`"` + k + `":`)
		b.WriteString(`"` + fmt.Sprintf("%v", obj[k]) + `"`)
	}
	b.WriteByte('}')
	return b.String()
}

func emitColumns(em *emitter, db *gorm.DB, goTable, ragTable string) {
	type row struct {
		ColumnName    string
		DataType      string
		IsNullable    string
		ColumnDefault string
	}
	var rows []row
	if err := db.Raw(
		"SELECT column_name, data_type, is_nullable, COALESCE(column_default, '') AS column_default "+
			"FROM information_schema.columns "+
			"WHERE table_schema = current_schema() AND table_name = ? ORDER BY ordinal_position",
		goTable).Scan(&rows).Error; err != nil {
		em.emit("c.ddl.col."+ragTable+".__error", "ERR:"+err.Error())
		return
	}
	parts := make([]string, 0, len(rows))
	for _, r := range rows {
		parts = append(parts, fmt.Sprintf("%s:%s:%s:%s", r.ColumnName, r.DataType, r.IsNullable, r.ColumnDefault))
	}
	// 逐列（**默认值已归一化**：序列名抹掉、大小写统一）⇒ 绝大多数列是「两侧逐字节相同」
	for _, r := range rows {
		em.emit(fmt.Sprintf("c.ddl.col.%s.%s", ragTable, r.ColumnName),
			fmt.Sprintf("%s:%s:%s", r.DataType, r.IsNullable, normalizeDefault(r.ColumnDefault)))
	}
}

var nextvalRe = regexp.MustCompile(`nextval\('[^']*'(::regclass)?\)`)

func normalizeDefault(def string) string {
	return strings.ToLower(nextvalRe.ReplaceAllString(def, "nextval(...)"))
}

func flagOr(prefix, def string) string {
	for _, a := range os.Args[1:] {
		if strings.HasPrefix(a, prefix) {
			return strings.TrimPrefix(a, prefix)
		}
	}
	return def
}
