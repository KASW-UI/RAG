package com.rag.favorite.favorite.rpc.service;

import com.rag.common.spring.SnowflakeBean;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteReq;
import com.rag.favorite.favorite.rpc.domain.FavoriteFolderEntity;
import com.rag.favorite.favorite.rpc.domain.FavoriteItemEntity;
import com.rag.favorite.favorite.rpc.exception.FavoriteExceptions;
import com.rag.favorite.favorite.rpc.mapper.FavoriteFolderMapper;
import com.rag.favorite.favorite.rpc.mapper.FavoriteItemMapper;
import com.rag.favorite.favorite.rpc.metrics.FavoriteRpcMetrics;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>错误码翻译表</b>的回归网 —— 逐条钉住 Go 侧 7 个 logic 的
 * {@code GRPCError(codes.X, favoritecommon.ErrorY)} 调用点。
 *
 * <h2>为什么必须是这一层</h2>
 * <p>错误码是 <b>L1 外部契约</b>：favorite-api 靠 {@code description} 的文案反查业务码，
 * 客户端拿到的 {@code code} 字段直接由它决定。
 * 而 {@link FavoriteDomainService} 里的「catch 分支 ⇒ 哪个码」是<b>纯逻辑</b>，
 * 没有任何框架机制参与 ⇒ 用 mock 打数据层是<b>恰当</b>的测法
 * （AGENTS.md §3.3.3 禁的是「用 mock 冒充框架机制生效」，不是禁 mock 测逻辑分支）。
 *
 * <h2>本类钉住的、曾经真的错过的分支（2026-10-23）</h2>
 * <ol>
 *   <li>🔴 <b>事务内收藏夹/条目消失 ⇒ 5008 而不是 5007</b>。
 *       Go 的三个事务写（favorite_fact.go:276-278 / 306-311 / 342-347）把
 *       「查不到」原样 <b>return err</b>，logic 层只区分
 *       {@code ErrFavoriteFactMigrationBlocked}（⇒5304）与「其它一切」（⇒ Internal/5008）。
 *       ⇒ <b>读路径的 5007 不能渗进写路径</b>。</li>
 *   <li>🔴 <b>事务内归属复核失败 ⇒ 5008 而不是 PermissionDenied</b>。
 *       Go 的 {@code ErrFavoriteOwnerMismatch} 在 logic 层不被单独翻译。</li>
 *   <li>🔴 <b>重名预查的 DB 错误 ⇒ 5007</b>（create_favorite_folder_logic.go:82-87）。
 *       漏掉这一支，异常会一路逃到 gRPC 兜底，变成 5001。</li>
 * </ol>
 */
class FavoriteErrorCodeMappingTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;
    private static final long FOLDER_ID = 3003L;
    private static final long FAVORITE_ID = 4004L;

    private FavoriteFolderMapper folderMapper;
    private FavoriteItemMapper itemMapper;
    private FavoriteFactWriteService factWriteService;
    private FavoriteDomainService service;

    @BeforeEach
    void setUp() {
        folderMapper = mock(FavoriteFolderMapper.class);
        itemMapper = mock(FavoriteItemMapper.class);
        factWriteService = mock(FavoriteFactWriteService.class);

        SnowflakeBean snowflake = mock(SnowflakeBean.class);
        when(snowflake.nextId()).thenReturn(9001L);

        service = new FavoriteDomainService(
                folderMapper,
                itemMapper,
                factWriteService,
                mock(FavoriteUserGuard.class),
                mock(FavoriteArticleSnapshotResolver.class),
                mock(FavoriteRpcMetrics.class),
                snowflake);
    }

    // ==================== createFavoriteFolder（Go create_favorite_folder_logic.go）====================

    @Test
    @DisplayName("🔴 重名预查命中 ⇒ AlreadyExists/5203（不是 5006「已存在」）")
    void folderNameClashIsAlreadyExists() {
        when(folderMapper.findByUserIdAndName(USER_ID, "dup")).thenReturn(folder(FOLDER_ID, USER_ID, "dup"));

        assertRpc(Status.Code.ALREADY_EXISTS, FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EXISTS,
                () -> service.createFavoriteFolder(
                        CreateFavoriteFolderReq.newBuilder().setUserId(USER_ID).setName("dup").build()));
    }

    @Test
    @DisplayName("🔴 重名预查的 DB 错误 ⇒ Internal/5007（漏掉会变成兜底的 5001）")
    void folderNamePreCheckDbErrorIsDbSelect() {
        when(folderMapper.findByUserIdAndName(anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("boom"));

        assertRpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_SELECT,
                () -> service.createFavoriteFolder(
                        CreateFavoriteFolderReq.newBuilder().setUserId(USER_ID).setName("f").build()));
    }

    @Test
    @DisplayName("insert 撞 23505 ⇒ AlreadyExists/5203")
    void folderInsertUniqueViolationIsAlreadyExists() {
        when(folderMapper.findByUserIdAndName(anyLong(), anyString())).thenReturn(null);
        doThrow(uniqueViolation()).when(folderMapper)
                .insertFolder(anyLong(), anyLong(), anyString(), any(), any());

        assertRpc(Status.Code.ALREADY_EXISTS, FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EXISTS,
                () -> service.createFavoriteFolder(
                        CreateFavoriteFolderReq.newBuilder().setUserId(USER_ID).setName("f").build()));
    }

    @Test
    @DisplayName("insert 其它 DB 错误 ⇒ Internal/5008（写路径，不是 5007）")
    void folderInsertDbErrorIsDbUpdate() {
        when(folderMapper.findByUserIdAndName(anyLong(), anyString())).thenReturn(null);
        doThrow(new DataAccessResourceFailureException("boom"))
                .when(folderMapper).insertFolder(anyLong(), anyLong(), anyString(), any(), any());

        assertRpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_UPDATE,
                () -> service.createFavoriteFolder(
                        CreateFavoriteFolderReq.newBuilder().setUserId(USER_ID).setName("f").build()));
    }

    // ==================== createFavorite（Go create_favorite_logic.go）====================

    @Test
    @DisplayName("收藏夹读失败 ⇒ Internal/5007")
    void favoriteFolderReadErrorIsDbSelect() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenThrow(new DataAccessResourceFailureException("boom"));

        assertRpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_SELECT,
                () -> service.createFavorite(createFavoriteReq()));
    }

    @Test
    @DisplayName("收藏夹不存在 ⇒ NotFound/5201")
    void favoriteFolderMissingIsNotFound() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenReturn(null);

        assertRpc(Status.Code.NOT_FOUND, FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NOT_FOUND,
                () -> service.createFavorite(createFavoriteReq()));
    }

    @Test
    @DisplayName("他人收藏夹 ⇒ PermissionDenied/5005")
    void foreignFolderIsForbidden() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenReturn(folder(FOLDER_ID, OTHER_USER_ID, "f"));

        assertRpc(Status.Code.PERMISSION_DENIED, FavoriteErrorCode.ERROR_FORBIDDEN,
                () -> service.createFavorite(createFavoriteReq()));
    }

    @Test
    @DisplayName("🔴 事务内收藏夹消失 ⇒ Internal/5008（**不是**读路径的 5007）")
    void folderVanishedInsideTransactionIsDbUpdate() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenReturn(folder(FOLDER_ID, USER_ID, "f"));
        when(itemMapper.findByFolderTarget(anyLong(), anyString(), anyString())).thenReturn(null);
        doThrow(new FavoriteExceptions.NotFoundException("favorite_folder"))
                .when(factWriteService).insertFavorite(any());

        assertRpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_UPDATE,
                () -> service.createFavorite(createFavoriteReq()));
    }

    @Test
    @DisplayName("🔴 事务内归属复核失败 ⇒ Internal/5008（**不是** PermissionDenied）")
    void ownerMismatchInsideTransactionIsDbUpdate() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenReturn(folder(FOLDER_ID, USER_ID, "f"));
        when(itemMapper.findByFolderTarget(anyLong(), anyString(), anyString())).thenReturn(null);
        doThrow(new FavoriteExceptions.OwnerMismatchException())
                .when(factWriteService).insertFavorite(any());

        assertRpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_UPDATE,
                () -> service.createFavorite(createFavoriteReq()));
    }

    @Test
    @DisplayName("insert 撞 23505 ⇒ AlreadyExists/5302")
    void favoriteInsertUniqueViolationIsAlreadyExists() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenReturn(folder(FOLDER_ID, USER_ID, "f"));
        when(itemMapper.findByFolderTarget(anyLong(), anyString(), anyString())).thenReturn(null);
        doThrow(uniqueViolation()).when(factWriteService).insertFavorite(any());

        assertRpc(Status.Code.ALREADY_EXISTS, FavoriteErrorCode.ERROR_FAVORITE_ALREADY_EXIST,
                () -> service.createFavorite(createFavoriteReq()));
    }

    // ==================== deleteFavorite（Go delete_favorite_logic.go）====================

    @Test
    @DisplayName("🔴 事务内条目消失 ⇒ Internal/5008（**不是**读路径的 5007）")
    void itemVanishedInsideTransactionIsDbUpdate() {
        when(itemMapper.findByFavoriteId(FAVORITE_ID)).thenReturn(item(FAVORITE_ID, USER_ID));
        doThrow(new FavoriteExceptions.NotFoundException("favorite_item"))
                .when(factWriteService).deleteFavoriteByFavoriteId(anyLong(), anyLong());

        assertRpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_UPDATE,
                () -> service.deleteFavorite(
                        DeleteFavoriteReq.newBuilder().setUserId(USER_ID).setFavoriteId(FAVORITE_ID).build()));
    }

    @Test
    @DisplayName("🔴 事实迁移未对齐 ⇒ FailedPrecondition/5304（唯一被单独翻译的事务错误）")
    void factMigrationBlockedIsFailedPrecondition() {
        when(itemMapper.findByFavoriteId(FAVORITE_ID)).thenReturn(item(FAVORITE_ID, USER_ID));
        doThrow(new FavoriteExceptions.FactMigrationBlockedException())
                .when(factWriteService).deleteFavoriteByFavoriteId(anyLong(), anyLong());

        assertRpc(Status.Code.FAILED_PRECONDITION, FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED,
                () -> service.deleteFavorite(
                        DeleteFavoriteReq.newBuilder().setUserId(USER_ID).setFavoriteId(FAVORITE_ID).build()));
    }

    // ==================== deleteFavoriteFolder（Go delete_favorite_folder_logic.go）====================

    @Test
    @DisplayName("🔴 事务内收藏夹消失 ⇒ Internal/5008（**不是**读路径的 5007）")
    void folderVanishedInsideCascadeIsDbUpdate() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenReturn(folder(FOLDER_ID, USER_ID, "f"));
        doThrow(new FavoriteExceptions.NotFoundException("favorite_folder"))
                .when(factWriteService).deleteFolderCascade(anyLong(), anyLong());

        assertRpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_UPDATE,
                () -> service.deleteFavoriteFolder(
                        DeleteFavoriteFolderReq.newBuilder()
                                .setUserId(USER_ID).setFolderId(FOLDER_ID).build()));
    }

    @Test
    @DisplayName("🔴 事实迁移未对齐 ⇒ FailedPrecondition/5304")
    void folderFactMigrationBlockedIsFailedPrecondition() {
        when(folderMapper.findByFolderId(FOLDER_ID)).thenReturn(folder(FOLDER_ID, USER_ID, "f"));
        doThrow(new FavoriteExceptions.FactMigrationBlockedException())
                .when(factWriteService).deleteFolderCascade(anyLong(), anyLong());

        assertRpc(Status.Code.FAILED_PRECONDITION, FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED,
                () -> service.deleteFavoriteFolder(
                        DeleteFavoriteFolderReq.newBuilder()
                                .setUserId(USER_ID).setFolderId(FOLDER_ID).build()));
    }

    // ==================== 工具 ====================

    /**
     * PG 的 unique_violation。
     *
     * <p>Go 侧靠 {@code pgconn.PgError.Code == "23505"} 判定（helper.go 的 isUniqueViolation），
     * Java 侧靠 SQLState ⇒ 这里必须造出带 {@code 23505} 的 {@link SQLException}，
     * 否则测的就不是「唯一键冲突」这条路径。
     */
    private static DuplicateKeyException uniqueViolation() {
        return new DuplicateKeyException("duplicate key", new SQLException("dup", "23505"));
    }

    private static FavoriteFolderEntity folder(long folderId, long userId, String name) {
        FavoriteFolderEntity entity = new FavoriteFolderEntity();
        entity.setFolderId(folderId);
        entity.setUserId(userId);
        entity.setName(name);
        return entity;
    }

    private static FavoriteItemEntity item(long favoriteId, long userId) {
        FavoriteItemEntity entity = new FavoriteItemEntity();
        entity.setFavoriteId(favoriteId);
        entity.setFolderId(FOLDER_ID);
        entity.setUserId(userId);
        entity.setTargetId("t1");
        entity.setTargetType("post");
        return entity;
    }

    private static CreateFavoriteReq createFavoriteReq() {
        return CreateFavoriteReq.newBuilder()
                .setUserId(USER_ID)
                .setFolderId(FOLDER_ID)
                .setTargetId("t1")
                .setTargetType("post")
                .build();
    }

    /** 断言 gRPC 码与业务码**同时**匹配 —— 只断言一个会漏掉「码对但状态码错」这类漂移。 */
    private static void assertRpc(Status.Code expectedGrpc,
                                  FavoriteErrorCode expectedBiz,
                                  org.junit.jupiter.api.function.Executable call) {
        FavoriteExceptions.Rpc e = assertThrows(FavoriteExceptions.Rpc.class, call);
        assertEquals(expectedGrpc, e.getGrpcCode(), "gRPC 状态码");
        assertEquals(expectedBiz.getCode(), e.getBizCode(), "业务码");
    }
}
