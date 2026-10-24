package com.rag.favorite.favorite.rpc.service;

import com.rag.common.logger.LogOption;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderResp;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.CreateFavoriteResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteResp;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderResp;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameResp;
import com.rag.favorite.favorite.rpc.config.FavoriteRpcLabels;
import com.rag.favorite.favorite.rpc.domain.FavoriteFolderEntity;
import com.rag.favorite.favorite.rpc.domain.FavoriteItemEntity;
import com.rag.favorite.favorite.rpc.exception.FavoriteExceptions;
import com.rag.favorite.favorite.rpc.mapper.FavoriteFolderMapper;
import com.rag.favorite.favorite.rpc.mapper.FavoriteItemMapper;
import com.rag.favorite.favorite.rpc.metrics.FavoriteRpcMetrics;
import com.rag.common.spring.SnowflakeBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 🔴 favorite 域的 7 个 gRPC 用例 —— 逐个对应 Go
 * {@code service/favorite/rpc/internal/logic/} 下的 7 个 logic 文件。
 *
 * <p>AGENTS.md §4.1 已锁定的决策：「9/13 个独立 Logic 合 1 个 DomainService」。
 * 本类与 users 域的 {@code UserDomainService}、like 域的
 * {@code LikeDomainService} 形态一致。
 *
 * <h2>逐用例对照表（Go 行号 ↔ 本方法）</h2>
 * <table border="1">
 *   <caption>7 个用例的分支与业务码</caption>
 *   <tr><th>Go logic</th><th>本方法</th><th>参数校验</th><th>依赖</th><th>归属</th><th>写</th></tr>
 *   <tr><td>{@code create_favorite_folder_logic.go}</td><td>{@link #createFavoriteFolder}</td>
 *       <td>uid&gt;0⇒5003；name 空⇒5202</td><td>ensureUserExists</td>
 *       <td>重名预查⇒5203</td><td>雪花 + insert（23505⇒5203，否则5008）</td></tr>
 *   <tr><td>{@code create_favorite_logic.go}</td><td>{@link #createFavorite}</td>
 *       <td>uid/folder&gt;0⇒5003；target 空⇒5303</td><td>ensureUserExists + 文章快照</td>
 *       <td>folder 存在⇒5201；归属⇒5005</td><td>雪花 + 事务写（23505⇒5302，否则5008）</td></tr>
 *   <tr><td>{@code update_favorite_folder_name_logic.go}</td><td>{@link #updateFavoriteFolderName}</td>
 *       <td>uid/folder&gt;0⇒5003；name 空⇒5202</td><td>—</td>
 *       <td>存在⇒5201；归属⇒5005；<b>同名直接成功不写库</b></td><td>update（23505⇒5203，否则5008）</td></tr>
 *   <tr><td>{@code delete_favorite_folder_logic.go}</td><td>{@link #deleteFavoriteFolder}</td>
 *       <td>uid/folder&gt;0⇒5003</td><td>—</td><td>存在⇒5201；归属⇒5005</td>
 *       <td>级联（<b>blocked⇒5304</b>，否则5008）</td></tr>
 *   <tr><td>{@code delete_favorite_logic.go}</td><td>{@link #deleteFavorite}</td>
 *       <td>uid/fav&gt;0⇒5003</td><td>—</td><td>存在⇒5301；归属⇒5005</td>
 *       <td>删除（<b>blocked⇒5304</b>，否则5008）</td></tr>
 *   <tr><td>{@code get_favorite_folders_logic.go}</td><td>{@link #getFavoriteFolders}</td>
 *       <td>uid&gt;0⇒5003</td><td>—</td><td>—</td><td>—</td></tr>
 *   <tr><td>{@code get_favorites_by_folder_logic.go}</td><td>{@link #getFavoritesByFolder}</td>
 *       <td>uid/folder&gt;0⇒5003</td><td>—</td>
 *       <td>存在⇒5201；归属⇒5005</td><td>—</td></tr>
 * </table>
 *
 * <h2>⚠️ 三个必须原样保留的「不优雅」行为</h2>
 * <ol>
 *   <li><b>DB 读失败一律 5007、DB 写失败一律 5008</b>，包括 23505 之外的所有写错误。</li>
 *   <li><b>删除路径的「历史未和解」⇒ {@code FailedPrecondition/5304}</b>，
 *       其它错误 ⇒ {@code Internal/5008}。注意 {@code OwnerMismatch}
 *       在 Go 侧<b>不</b>单独翻译，会落进 5008。</li>
 *   <li><b>同名更新直接返回成功、不写库</b>
 *       （{@code update_favorite_folder_name_logic.go:91-95}）——
 *       所以 {@code updated_at} <b>不会</b>因为「改了个一样的名字」而推进。</li>
 * </ol>
 */
@Service
public class FavoriteDomainService {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteDomainService.class);

    private final FavoriteFolderMapper folderMapper;
    private final FavoriteItemMapper itemMapper;
    private final FavoriteFactWriteService factWriteService;
    private final FavoriteUserGuard userGuard;
    private final FavoriteArticleSnapshotResolver articleResolver;
    private final FavoriteRpcMetrics metrics;
    private final SnowflakeBean snowflake;

    public FavoriteDomainService(FavoriteFolderMapper folderMapper,
                                 FavoriteItemMapper itemMapper,
                                 FavoriteFactWriteService factWriteService,
                                 FavoriteUserGuard userGuard,
                                 FavoriteArticleSnapshotResolver articleResolver,
                                 FavoriteRpcMetrics metrics,
                                 SnowflakeBean snowflake) {
        this.folderMapper = folderMapper;
        this.itemMapper = itemMapper;
        this.factWriteService = factWriteService;
        this.userGuard = userGuard;
        this.articleResolver = articleResolver;
        this.metrics = metrics;
        this.snowflake = snowflake;
    }

    // ══════════════════════════ 1. 新增收藏夹 ══════════════════════════

    /**
     * 对应 Go {@code CreateFavoriteFolderLogic.CreateFavoriteFolder}（create_favorite_folder_logic.go:37-118）。
     */
    public CreateFavoriteFolderResp createFavoriteFolder(CreateFavoriteFolderReq request) {
        Instant started = Instant.now();
        String result = FavoriteRpcLabels.RESULT_SUCCESS;
        try {
            long userId = request.getUserId();
            // Go: :51-57 —— userId <= 0 ⇒ InvalidArgument/5003
            if (userId <= 0) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.invalidParam();
            }

            // Go: :59-66 —— normalizeFolderName(name) == "" ⇒ InvalidArgument/5202
            String name = FavoriteRpcSupport.normalizeFolderName(request.getName());
            if (name.isEmpty()) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.folderNameEmpty();
            }

            // Go: :68-73
            userGuard.ensureExists(userId, FavoriteRpcSupport.userLogOption(userId));

            // Go: :75-87 —— 重名预查：命中 ⇒ AlreadyExists/5203；其它 DB 错误 ⇒ Internal/5007
            // 🔴 必须包 try：Go 的 :82-87 有「查得到 ⇒ 5203 / 查不到 ⇒ 继续 /
            //    **DB 出错 ⇒ 5007**」三分支。漏掉 DB 出错这一支，异常会一路逃到
            //    gRPC 兜底，变成 5001 而不是 Go 的 5007（对外可观察的码变了）。
            try {
                FavoriteFolderEntity existing = folderMapper.findByUserIdAndName(userId, name);
                if (existing != null) {
                    result = FavoriteRpcLabels.RESULT_FAIL;
                    throw FavoriteExceptions.folderNameExists();
                }
            } catch (FavoriteExceptions.Rpc e) {
                throw e;
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbSelect();
            }

            // Go: :89-95 —— snowflake.GetID() 失败 ⇒ Internal/5009
            long folderId = nextId();

            // Go: :97-113 —— InsertFolder：23505 ⇒ 5203，否则 Internal/5008
            try {
                folderMapper.insertFolder(folderId, userId, name);
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                if (FavoriteRpcSupport.isUniqueViolation(e)) {
                    metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                            FavoriteRpcLabels.DB_ACTION_INSERT, FavoriteRpcLabels.DB_TYPE_DUPLICATE);
                    throw FavoriteExceptions.folderNameExists();
                }
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_INSERT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            }

            metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_CREATE,
                    FavoriteRpcLabels.RESULT_SUCCESS);
            LOG.info("favorite folder created, folder_id={}", folderId,
                    FavoriteRpcSupport.userLogOption(userId));
            return CreateFavoriteFolderResp.newBuilder().setFolderId(folderId).build();
        } catch (FavoriteExceptions.Rpc e) {
            if (FavoriteRpcLabels.RESULT_FAIL.equals(result)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_CREATE,
                        FavoriteRpcLabels.RESULT_FAIL);
            }
            throw e;
        } finally {
            metrics.observeRpc(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_CREATE,
                    started, result);
        }
    }

    // ══════════════════════════ 2. 新增收藏 ══════════════════════════

    /**
     * 🔴 对应 Go {@code CreateFavoriteLogic.CreateFavorite}（create_favorite_logic.go:38-170）——
     * 本域<b>最复杂</b>的一个用例。
     *
     * <p>顺序不可调换（Go 的执行序）：
     * 参数校验 → 用户身份 → 收藏夹存在性 → <b>归属</b> → 文章快照冻结 →
     * 重查重名 → 雪花 ID → 事务写。
     */
    public CreateFavoriteResp createFavorite(CreateFavoriteReq request) {
        Instant started = Instant.now();
        String result = FavoriteRpcLabels.RESULT_SUCCESS;
        long userId = request.getUserId();
        LogOption userLog = FavoriteRpcSupport.userLogOption(userId);
        try {
            // Go: :54-60
            if (userId <= 0 || request.getFolderId() <= 0) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.invalidParam();
            }

            // Go: :62-70 —— normalizeTargetType + TrimSpace
            String targetType = FavoriteRpcSupport.normalizeTargetType(request.getTargetType());
            String targetId = request.getTargetId() == null ? "" : request.getTargetId().trim();
            if (targetId.isEmpty() || targetType.isEmpty()) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.favoriteTargetEmpty();
            }

            // Go: :72-77
            userGuard.ensureExists(userId, userLog);

            // Go: :79-93 —— 收藏夹存在性：NotFound ⇒ 5201；其它 DB 错误 ⇒ Internal/5007
            FavoriteFolderEntity folder;
            try {
                folder = folderMapper.findByFolderId(request.getFolderId());
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbSelect();
            }
            if (folder == null) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.folderNotFound();
            }

            // Go: :94-100 —— 归属：folder.UserId != userId ⇒ PermissionDenied/5005
            if (!folder.getUserId().equals(userId)) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.forbidden();
            }

            // Go: :102-120 —— 文章快照：title/cover/revision 只来自公开投影
            String title = request.getTitle() == null ? "" : request.getTitle().trim();
            String cover = request.getCover() == null ? "" : request.getCover().trim();
            String targetRevision = null;
            if ("article".equals(targetType)) {
                FavoriteArticleSnapshotResolver.ArticleSnapshot snapshot =
                        articleResolver.resolve(targetId, userLog,
                                FavoriteRpcSupport.articleLogOption(targetId));
                // Go: :115 —— title, cover = snapshot.Title, snapshot.Cover
                // 🔴 请求里的 title/cover 在此被**无条件覆盖**。
                title = snapshot.title;
                cover = snapshot.cover;
                // Go: :116-119
                if (snapshot.hasRevision()) {
                    targetRevision = snapshot.revisionId;
                }
            }

            // Go: :122-134 —— 重查重名：命中 ⇒ AlreadyExists/5302；其它 DB 错误 ⇒ 5007
            try {
                FavoriteItemEntity duplicate =
                        itemMapper.findByFolderTarget(request.getFolderId(), targetId, targetType);
                if (duplicate != null) {
                    result = FavoriteRpcLabels.RESULT_FAIL;
                    throw FavoriteExceptions.favoriteAlreadyExist();
                }
            } catch (FavoriteExceptions.Rpc e) {
                throw e;
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbSelect();
            }

            // Go: :136-142 —— 雪花 ID
            long favoriteId = nextId();

            // Go: :144-165 —— 组装并事务写
            FavoriteItemEntity item = new FavoriteItemEntity();
            item.setFavoriteId(favoriteId);
            item.setFolderId(request.getFolderId());
            item.setUserId(userId);
            item.setTargetId(targetId);
            item.setTargetType(targetType);
            item.setTargetRevision(targetRevision);
            item.setTitle(title);
            item.setCover(cover);

            try {
                factWriteService.insertFavorite(item);
            } catch (FavoriteExceptions.OwnerMismatchException e) {
                // Go: :280-282 ⇒ 普通 error ⇒ Internal/5008（**不是** PermissionDenied）
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_INSERT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            } catch (FavoriteExceptions.NotFoundException e) {
                // 🔴 Go: create_favorite_logic.go:154-165 —— InsertFavorite 的**所有**非 23505 错误
                //    （含 favorite_fact.go:276-278 事务内收藏夹消失返回的 ErrRecordNotFound）
                //    一律 Internal/ErrorDbUpdate(5008)，**不是** 5007。
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_INSERT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            } catch (FavoriteExceptions.Rpc e) {
                throw e;
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                // Go: :157-161 —— 23505 ⇒ AlreadyExists/5302
                if (FavoriteRpcSupport.isUniqueViolation(e)) {
                    metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                            FavoriteRpcLabels.DB_ACTION_INSERT, FavoriteRpcLabels.DB_TYPE_DUPLICATE);
                    throw FavoriteExceptions.favoriteAlreadyExist();
                }
                // Go: :162-164
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_INSERT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            }

            metrics.observeOp(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_CREATE,
                    FavoriteRpcLabels.RESULT_SUCCESS);
            LOG.info("favorite item created, favorite_id={}", favoriteId,
                    userLog, FavoriteRpcSupport.articleLogOption(targetId));
            return CreateFavoriteResp.newBuilder().setFavoriteId(favoriteId).build();
        } catch (FavoriteExceptions.Rpc e) {
            if (FavoriteRpcLabels.RESULT_FAIL.equals(result)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_CREATE,
                        FavoriteRpcLabels.RESULT_FAIL);
            }
            throw e;
        } finally {
            metrics.observeRpc(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_CREATE,
                    started, result);
        }
    }

    // ══════════════════════════ 3. 删除收藏夹 ══════════════════════════

    /** 对应 Go {@code DeleteFavoriteFolderLogic}（delete_favorite_folder_logic.go:36-96）。 */
    public DeleteFavoriteFolderResp deleteFavoriteFolder(DeleteFavoriteFolderReq request) {
        Instant started = Instant.now();
        String result = FavoriteRpcLabels.RESULT_SUCCESS;
        long userId = request.getUserId();
        long folderId = request.getFolderId();
        LogOption userLog = FavoriteRpcSupport.userLogOption(userId);
        try {
            // Go: :50-56
            if (userId <= 0 || folderId <= 0) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.invalidParam();
            }

            FavoriteFolderEntity folder = requireOwnedFolder(userId, folderId);
            if (folder == null) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.folderNotFound();
            }
            if (!folder.getUserId().equals(userId)) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.forbidden();
            }

            // Go: :81-91 —— DeleteFolderCascade
            try {
                factWriteService.deleteFolderCascade(folderId, userId);
            } catch (FavoriteExceptions.FactMigrationBlockedException e) {
                // 🔴 Go: :84-87 —— FailedPrecondition/5304
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.historyBlocked();
            } catch (FavoriteExceptions.OwnerMismatchException e) {
                // Go: :349-351 ⇒ 普通 error ⇒ Internal/5008
                // ⚠️ Go: delete_favorite_folder_logic.go:88 ⇒ ObserveDBError(**folderModule**, "delete", "db")
                //    —— 删的是**收藏夹**级联 ⇒ folderModule，不是 itemModule。
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_DELETE, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            } catch (FavoriteExceptions.NotFoundException e) {
                // 🔴 Go: delete_favorite_folder_logic.go:81-90 —— DeleteFolderCascade 的**所有**
                //    非 blocked 错误（含 favorite_fact.go:342-347 事务内收藏夹消失）⇒ Internal/5008。
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_DELETE, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_DELETE, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            }

            metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_DELETE,
                    FavoriteRpcLabels.RESULT_SUCCESS);
            LOG.info("favorite folder deleted, folder_id={}", folderId, userLog);
            return DeleteFavoriteFolderResp.newBuilder().setSuccess(true).build();
        } catch (FavoriteExceptions.Rpc e) {
            if (FavoriteRpcLabels.RESULT_FAIL.equals(result)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_DELETE,
                        FavoriteRpcLabels.RESULT_FAIL);
            }
            throw e;
        } finally {
            metrics.observeRpc(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_DELETE,
                    started, result);
        }
    }

    // ══════════════════════════ 4. 删除收藏 ══════════════════════════

    /** 对应 Go {@code DeleteFavoriteLogic}（delete_favorite_logic.go:36-96）。 */
    public DeleteFavoriteResp deleteFavorite(DeleteFavoriteReq request) {
        Instant started = Instant.now();
        String result = FavoriteRpcLabels.RESULT_SUCCESS;
        long userId = request.getUserId();
        long favoriteId = request.getFavoriteId();
        LogOption userLog = FavoriteRpcSupport.userLogOption(userId);
        try {
            // Go: :50-56
            if (userId <= 0 || favoriteId <= 0) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.invalidParam();
            }

            // Go: :58-79 —— 存在性（NotFound ⇒ 5301）+ 归属（5005）
            FavoriteItemEntity favorite;
            try {
                favorite = itemMapper.findByFavoriteId(favoriteId);
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbSelect();
            }
            if (favorite == null) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.favoriteNotFound();
            }
            if (!favorite.getUserId().equals(userId)) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.forbidden();
            }

            // Go: :81-91
            try {
                factWriteService.deleteFavoriteByFavoriteId(favoriteId, userId);
            } catch (FavoriteExceptions.FactMigrationBlockedException e) {
                // 🔴 Go: :84-87 —— FailedPrecondition/5304
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.historyBlocked();
            } catch (FavoriteExceptions.OwnerMismatchException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_DELETE, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            } catch (FavoriteExceptions.NotFoundException e) {
                // 🔴 Go: delete_favorite_logic.go:81-91 —— DeleteFavoriteByFavoriteId 的**所有**
                //    非 blocked 错误（含 favorite_fact.go:306-311 事务内条目/收藏夹消失）⇒ Internal/5008。
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_DELETE, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_DELETE, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            }

            metrics.observeOp(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_DELETE,
                    FavoriteRpcLabels.RESULT_SUCCESS);
            LOG.info("favorite item deleted, favorite_id={}", favoriteId, userLog);
            return DeleteFavoriteResp.newBuilder().setSuccess(true).build();
        } catch (FavoriteExceptions.Rpc e) {
            if (FavoriteRpcLabels.RESULT_FAIL.equals(result)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_DELETE,
                        FavoriteRpcLabels.RESULT_FAIL);
            }
            throw e;
        } finally {
            metrics.observeRpc(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_DELETE,
                    started, result);
        }
    }

    // ══════════════════════════ 5. 修改收藏夹名字 ══════════════════════════

    /** 对应 Go {@code UpdateFavoriteFolderNameLogic}（update_favorite_folder_name_logic.go:36-127）。 */
    public UpdateFavoriteFolderNameResp updateFavoriteFolderName(UpdateFavoriteFolderNameReq request) {
        Instant started = Instant.now();
        String result = FavoriteRpcLabels.RESULT_SUCCESS;
        long userId = request.getUserId();
        long folderId = request.getFolderId();
        LogOption userLog = FavoriteRpcSupport.userLogOption(userId);
        try {
            // Go: :51-57
            if (userId <= 0 || folderId <= 0) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.invalidParam();
            }
            // Go: :59-66
            String name = FavoriteRpcSupport.normalizeFolderName(request.getName());
            if (name.isEmpty()) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.folderNameEmpty();
            }

            // Go: :68-89 —— 存在性（5201）+ 归属（5005）
            FavoriteFolderEntity folder = requireOwnedFolder(userId, folderId);
            if (folder == null) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.folderNotFound();
            }
            if (!folder.getUserId().equals(userId)) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.forbidden();
            }

            // 🔴 Go: :91-95 —— 「新名字 == 旧名字」⇒ 直接返回成功，**不写库**
            // ⇒ updated_at 不会被推进。这是 L2 语义，不是优化。
            if (folder.getName().equals(name)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_UPDATE_NAME,
                        FavoriteRpcLabels.RESULT_SUCCESS);
                LOG.info("favorite folder name unchanged, folder_id={}", folderId, userLog);
                return UpdateFavoriteFolderNameResp.newBuilder().setSuccess(true).build();
            }

            // Go: :97-109 —— 换名冲突检查：只有 existing.FolderId != folderId 才算冲突
            try {
                FavoriteFolderEntity clash = folderMapper.findByUserIdAndName(userId, name);
                if (clash != null && !clash.getFolderId().equals(folderId)) {
                    result = FavoriteRpcLabels.RESULT_FAIL;
                    throw FavoriteExceptions.folderNameExists();
                }
            } catch (FavoriteExceptions.Rpc e) {
                throw e;
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbSelect();
            }

            // Go: :111-122 —— Update：23505 ⇒ 5203，否则 Internal/5008
            try {
                folderMapper.updateNameByFolderId(folderId, name);
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                if (FavoriteRpcSupport.isUniqueViolation(e)) {
                    metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                            FavoriteRpcLabels.DB_ACTION_UPDATE, FavoriteRpcLabels.DB_TYPE_DUPLICATE);
                    throw FavoriteExceptions.folderNameExists();
                }
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_UPDATE, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbUpdate();
            }

            metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_UPDATE_NAME,
                    FavoriteRpcLabels.RESULT_SUCCESS);
            LOG.info("favorite folder name updated, folder_id={}", folderId, userLog);
            return UpdateFavoriteFolderNameResp.newBuilder().setSuccess(true).build();
        } catch (FavoriteExceptions.Rpc e) {
            if (FavoriteRpcLabels.RESULT_FAIL.equals(result)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_UPDATE_NAME,
                        FavoriteRpcLabels.RESULT_FAIL);
            }
            throw e;
        } finally {
            metrics.observeRpc(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_UPDATE_NAME,
                    started, result);
        }
    }

    // ══════════════════════════ 6. 查询收藏夹 ══════════════════════════

    /** 对应 Go {@code GetFavoriteFoldersLogic}（get_favorite_folders_logic.go:34-70）。 */
    public GetFavoriteFoldersResp getFavoriteFolders(GetFavoriteFoldersReq request) {
        Instant started = Instant.now();
        String result = FavoriteRpcLabels.RESULT_SUCCESS;
        long userId = request.getUserId();
        LogOption userLog = FavoriteRpcSupport.userLogOption(userId);
        try {
            // Go: :45-51 —— ⚠️ 注意：list 用例**不**调 ensureUserExists
            if (userId <= 0) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.invalidParam();
            }

            List<FavoriteFolderEntity> folders;
            try {
                folders = folderMapper.findByUserId(userId);
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                        FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbSelect();
            }

            // Go: :62-65 —— make([]*FavoriteFolder, 0, len)：空集合是<b>空数组</b>不是 null
            List<com.rag.favorite.favorite.proto.FavoriteFolder> pbFolders = new ArrayList<>(folders.size());
            for (FavoriteFolderEntity folder : folders) {
                pbFolders.add(FavoriteRpcSupport.toProtoFolder(folder));
            }

            metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_LIST,
                    FavoriteRpcLabels.RESULT_SUCCESS);
            LOG.info("favorite folders listed, count={}", pbFolders.size(), userLog);
            return GetFavoriteFoldersResp.newBuilder().addAllFolders(pbFolders).build();
        } catch (FavoriteExceptions.Rpc e) {
            if (FavoriteRpcLabels.RESULT_FAIL.equals(result)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_LIST,
                        FavoriteRpcLabels.RESULT_FAIL);
            }
            throw e;
        } finally {
            metrics.observeRpc(FavoriteRpcLabels.MODULE_FOLDER, FavoriteRpcLabels.ACTION_LIST,
                    started, result);
        }
    }

    // ══════════════════════════ 7. 查询收藏夹下的收藏 ══════════════════════════

    /** 对应 Go {@code GetFavoritesByFolderLogic}（get_favorites_by_folder_logic.go:36-98）。 */
    public GetFavoritesByFolderResp getFavoritesByFolder(GetFavoritesByFolderReq request) {
        Instant started = Instant.now();
        String result = FavoriteRpcLabels.RESULT_SUCCESS;
        long userId = request.getUserId();
        long folderId = request.getFolderId();
        LogOption userLog = FavoriteRpcSupport.userLogOption(userId);
        try {
            // Go: :50-56
            if (userId <= 0 || folderId <= 0) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.invalidParam();
            }

            // Go: :58-79 —— 存在性（5201）+ 归属（5005）
            FavoriteFolderEntity folder = requireOwnedFolder(userId, folderId);
            if (folder == null) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.folderNotFound();
            }
            if (!folder.getUserId().equals(userId)) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                throw FavoriteExceptions.forbidden();
            }

            List<FavoriteItemEntity> favorites;
            try {
                favorites = itemMapper.findByFolderId(folderId);
            } catch (DataAccessException e) {
                result = FavoriteRpcLabels.RESULT_FAIL;
                // Go: get_favorites_by_folder_logic.go:84 ⇒ ObserveDBError(**itemModule**, "select", "db")
                // ⚠️ 收藏**条目**的读失败 ⇒ itemModule（"favorite_item"）。
                //    上一行的收藏夹读失败才是 folderModule（Go: :68）—— 两者不可混。
                metrics.observeDbError(FavoriteRpcLabels.MODULE_ITEM,
                        FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
                throw FavoriteExceptions.dbSelect();
            }

            List<com.rag.favorite.favorite.proto.FavoriteItem> pbItems = new ArrayList<>(favorites.size());
            for (FavoriteItemEntity item : favorites) {
                pbItems.add(FavoriteRpcSupport.toProtoItem(item));
            }

            metrics.observeOp(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_LIST_BY_FOLDER,
                    FavoriteRpcLabels.RESULT_SUCCESS);
            LOG.info("favorite items listed, count={}", pbItems.size(), userLog);
            return GetFavoritesByFolderResp.newBuilder().addAllFavorites(pbItems).build();
        } catch (FavoriteExceptions.Rpc e) {
            if (FavoriteRpcLabels.RESULT_FAIL.equals(result)) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_LIST_BY_FOLDER,
                        FavoriteRpcLabels.RESULT_FAIL);
            }
            throw e;
        } finally {
            metrics.observeRpc(FavoriteRpcLabels.MODULE_ITEM, FavoriteRpcLabels.ACTION_LIST_BY_FOLDER,
                    started, result);
        }
    }

    // ══════════════════════════ 内部工具 ══════════════════════════

    /**
     * 读收藏夹；DB 异常按 Go 的 5007 路径处理（记 db_error 后抛）。
     *
     * <p>⚠️ <b>调用方拿不到异常</b> —— Go 侧三处（delete_favorite_folder_logic.go:58-72、
     * update_favorite_folder_name:68-82、get_favorites_by_folder:58-72）形状一致：
     * {@code if dbErr != nil { NotFound ? 5201 : 5007 }}。
     */
    private FavoriteFolderEntity requireOwnedFolder(long userId, long folderId) {
        try {
            return folderMapper.findByFolderId(folderId);
        } catch (DataAccessException e) {
            metrics.observeDbError(FavoriteRpcLabels.MODULE_FOLDER,
                    FavoriteRpcLabels.DB_ACTION_SELECT, FavoriteRpcLabels.DB_TYPE_DB);
            throw FavoriteExceptions.dbSelect();
        }
    }

    /**
     * Go {@code snowflake.GetID()}（create_favorite_folder_logic.go:89、create_favorite_logic.go:136）。
     *
     * <p>🔴 失败 ⇒ {@code Internal/5009}（{@code ErrorGenerateID}）。
     * ⚠️ favorite 只用雪花生成 folder_id / favorite_id，
     * <b>不用于游标</b>（两个 list 用例全量返回，无分页）。
     */
    private long nextId() {
        long id = snowflake.nextId();
        if (id <= 0) {
            throw FavoriteExceptions.generateId();
        }
        return id;
    }
}