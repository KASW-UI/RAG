--
-- V1__create_favorite_folder.sql
-- 对应 Go 侧 DDL 事实源之一：gorm AutoMigrate 的 FavoriteFolder 实体
--   Sea-RideTheWind_golang/service/favorite/rpc/internal/model/data_model.go:5-16
--   由 internal/model/db.go:33  db.AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{}) 产出
--
-- 列类型依据不是 struct tag 的字面，而是 GORM 的类型推断链（AGENTS.md §3.3.1：证据要指向消费点）：
--   ① gorm.io/driver/postgres 对 Go 的 uint64 / int64 → bigint
--   ② tag 里的 type:varchar(N) 逐字照抄
--   ③ time.Time → timestamptz（PG driver 默认）
--   ④ 未标 not null 的 string（Name 标了，Id 走自增）按 GORM 惯例
--
-- ⚠️ 本表**不含** CHECK / 触发器（Go 侧也没有）；favorite 域的两个不可变触发器在 V5 / V4。
CREATE TABLE IF NOT EXISTS favorite_folder (
    -- Go: Id uint64 `gorm:"primaryKey"` —— 自增代理键，业务从不使用
    id           bigserial   PRIMARY KEY,
    -- Go: FolderId int64 `gorm:"column:folder_id;uniqueIndex;not null"`
    folder_id    bigint      NOT NULL,
    -- Go: UserId int64 `gorm:"column:user_id;not null;index;uniqueIndex:uk_user_folder_name"`
    user_id      bigint      NOT NULL,
    -- Go: Name string `gorm:"column:name;type:varchar(100);not null;uniqueIndex:uk_user_folder_name"`
    name         varchar(100) NOT NULL,
    -- Go: CreateTime time.Time `gorm:"column:create_time;autoCreateTime"`
    create_time  timestamptz,
    -- Go: UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`
    update_time  timestamptz
);

-- 🔴 uk_user_folder_name 是 L2 业务语义，不是性能索引：
--   CreateFavoriteFolderLogic（create_favorite_folder_logic.go:75-87）先查重再报 AlreadyExists/5203，
--   InsertFolder 的 23505 也被翻译成同一个码（:105-109）。
--   删掉这个约束 ⇒ 并发下两个同名收藏夹都能建出来，重名保护失效。
CREATE UNIQUE INDEX IF NOT EXISTS uk_favorite_folder_folder_id
    ON favorite_folder (folder_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_folder_name
    ON favorite_folder (user_id, name);

CREATE INDEX IF NOT EXISTS idx_favorite_folder_user_id
    ON favorite_folder (user_id);

-- ⚠️ GORM 的命名：column 上的 uniqueIndex 生成 idx_<table>_<column>；
--   复合索引名直接取 tag 里的名字。保持与 Go AutoMigrate 产物同名，便于两侧对拍。