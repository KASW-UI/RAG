package com.rag.article.article.rpc.bootstrap;

import com.rag.article.article.rpc.client.ArticleObjectStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动期幂等创建对象存储桶并设置公开读策略 —— 对应 Go 端
 * {@code internal/svc/service_context.go:43-56}。
 *
 * <h2>⚠️ 语义逐条对照 Go（这段看起来"可以简化"，但每条都有行为含义）</h2>
 * <pre>
 * err = MakeBucket(...)
 * if err != nil {
 *     exists, errBucketExists := BucketExists(...)
 *     if errBucketExists == nil &amp;&amp; exists {
 *         // 已存在：<b>什么都不做</b>（连 warn 都没有，也<b>不</b>去修正策略）
 *     } else {
 *         logx.Errorf("create minio bucket failed: %v", err)
 *     }
 * } else {
 *     err = SetBucketPolicy(public-read)
 *     if err != nil { logx.Errorf("set minio bucket policy failed: %v", err) }
 * }
 * </pre>
 * ⇒ <b>只有「本次真的创建成功」才设置 public-read 策略</b>。
 * 改成"每次启动都确保策略正确"会在运维<b>刻意收紧</b>桶策略后被自动改回去 ——
 * 那是<b>新增的运维行为</b>，不是对齐。
 *
 * <p><b>失败不阻止启动</b>：Go 侧这里只记日志（与 {@code minio.New} 失败才 panic 形成不对称），
 * 因为 gRPC server 与 Kafka 消费不依赖桶当时可用。
 * 照搬：{@code ApplicationReadyEvent} 里记日志即可。
 *
 * <p><b>为什么挂在 {@code ApplicationReadyEvent} 而不是构造器</b>：
 * Go 侧是在 {@code NewServiceContext}（进程装配期）做的；
 * Java 侧放构造期会让<b>单元测试无法构造该 Bean</b>（它会去连对象存储），
 * 且启动日志里对象存储的错误会淹没真正的启动失败。事件回调是等价且更安全的时点。
 */
@Component
public class MinioBucketBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(MinioBucketBootstrap.class);

    private final ArticleObjectStorageClient storage;

    public MinioBucketBootstrap(ArticleObjectStorageClient storage) {
        this.storage = storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            boolean created = storage.ensureBucketWithPublicRead();
            if (created) {
                LOG.info("MinioBucketBootstrap: bucket {} created with public-read policy",
                        storage.getBucketName());
            } else {
                // Go 侧这一分支是完全静默的；这里记一条 info 便于排障，
                // 属纯观测增强，不改变任何行为。
                LOG.info("MinioBucketBootstrap: bucket {} already exists, policy left untouched",
                        storage.getBucketName());
            }
        } catch (Exception e) {
            // Go: logx.Errorf("create minio bucket failed: %v", err) —— 不 panic
            LOG.error("MinioBucketBootstrap: ensure bucket {} failed (not fatal): {}",
                    storage.getBucketName(), e.getMessage(), e);
        }
    }
}