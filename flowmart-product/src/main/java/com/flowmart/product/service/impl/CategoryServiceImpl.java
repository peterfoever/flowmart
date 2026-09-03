package com.flowmart.product.service.impl;


import com.flowmart.common.exception.BizException;
import com.flowmart.product.checker.CategoryDeleteChecker;
import com.flowmart.product.command.DeleteCategoryCommand;
import com.flowmart.product.context.CategoryDeleteContext;
import com.flowmart.product.convert.CategoryConverter;
import com.flowmart.product.dto.CreateCategoryDTO;
import com.flowmart.product.dto.MoveCategoryDTO;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.CategoryStatus;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.service.CategoryService;


import com.flowmart.product.vo.CategoryTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private final ProductCategoryMapper productCategoryMapper;
    private final CategoryConverter categoryConverter;
    private final List<CategoryDeleteChecker> categoryDeleteCheckers;
    private static final Long SYSTEM_OPERATOR_ID = 0L;
    /**
     * 类目最大层级
     */
    private static final int MAX_LEVEL = 5;

    public CategoryServiceImpl(ProductCategoryMapper productCategoryMapper,
                               CategoryConverter categoryConverter,
                               List<CategoryDeleteChecker> categoryDeleteCheckers) {
        this.productCategoryMapper = productCategoryMapper;
        this.categoryConverter = categoryConverter;
        this.categoryDeleteCheckers = categoryDeleteCheckers;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CreateCategoryDTO categoryDTO) {
        Integer level;
        if (categoryDTO.getParentId() == null || categoryDTO.getParentId() == 0) {
            // 一级类目
            level = 1;
        } else {
//            非一级类目
            ProductCategory parent = productCategoryMapper.selectById(categoryDTO.getParentId());
            if (parent == null) {
                throw new BizException(ProductErrorCode.CATEGORY_PARENT_NOT_FOUND);
            }
            if (CategoryStatus.DISABLED.matches(parent.getStatus())) {
                throw new BizException(ProductErrorCode.CATEGORY_PARENT_DISABLED);
            }
            level = parent.getLevel() + 1;

        }
        // ========== 2. 校验层级上限 ==========
        if (level > MAX_LEVEL) {
            throw new BizException(ProductErrorCode.CATEGORY_LEVEL_EXCEEDED);
        }
        // ========== 3. 校验同级名称重复（业务校验，给用户友好提示） ==========
        boolean exists = productCategoryMapper.existsByNameAndParent(
                categoryDTO.getParentId() == null ? 0L : categoryDTO.getParentId(),
                categoryDTO.getName(),
                null  // 新增时不需要排除自身
        );
        if (exists) {
            throw new BizException(ProductErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        // ========== 4. 转换并填充 ==========
        ProductCategory entity = categoryConverter.toEntity(categoryDTO);

        entity.setLevel(level);


        try {
            int rows = productCategoryMapper.insert(entity);
            if (rows != 1) {
                log.error("插入类目失败: rows={}, entity={}", rows, entity);
                throw new BizException(ProductErrorCode.CATEGORY_CREATE_FAILED);
            }
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一索引 uk_parent_name_deleted 触发
            // 这是数据库层面的最后一道防线
            log.warn("并发创建同名类目冲突：parentId={}, name={}", categoryDTO.getParentId(), categoryDTO.getName(), e);
            throw new BizException(ProductErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        // ========== 6. 日志 ==========
        log.info("创建类目成功: categoryId={}, parentId={}, name={}, level={}",
                entity.getId(), entity.getParentId(), entity.getName(), entity.getLevel());

        // ========== 7. 返回 ==========
        return entity.getId();
    }

    @Override
    public List<CategoryTreeVO> listChildren(Long parentId) {
        List<ProductCategory> productCategories = productCategoryMapper.selectByParentId(parentId);
        if (productCategories == null || productCategories.isEmpty()) {

            return List.of();
        }
        return categoryConverter.toTreeVOList(productCategories);

    }

    @Override
    public List<CategoryTreeVO> listFrontTree() {
        log.info("查询前台类目树");
        List<ProductCategory> productCategories = productCategoryMapper.selectAllUndeletedOrdered();
        log.info("查询到{}条类目数据", productCategories.size());

        if (CollectionUtils.isEmpty(productCategories)) {
            return Collections.emptyList();
        }

        //构建parentId -> list<categories>映射
        Map<Long, List<ProductCategory>> parentMap = productCategories.stream().collect(Collectors.groupingBy(ProductCategory::getParentId));

        return buildChildren(0L, parentMap, true, true);
    }

    @Override
    public List<CategoryTreeVO> listAdminTree() {
        log.info("查询后台类目树");
        List<ProductCategory> productCategories = productCategoryMapper.selectAllUndeletedOrdered();
        log.info("查询到{}条类目数据", productCategories.size());
        if (CollectionUtils.isEmpty(productCategories)) {
            return Collections.emptyList();
        }
        //构建parentId -> list<categories>映射
        Map<Long, List<ProductCategory>> parentMap = productCategories.stream().collect(Collectors.groupingBy(ProductCategory::getParentId));
        return buildChildren(0L, parentMap, false, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(DeleteCategoryCommand command) {
        ProductCategory productCategory = productCategoryMapper.selectById(command.getCategoryId());
        if (productCategory == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        List<ProductCategory> directChildren = safeList(productCategoryMapper.selectByParentId(command.getCategoryId()));
        List<ProductCategory> targetParentChildren = safeList(productCategoryMapper.selectByParentId(productCategory.getParentId()));
        List<ProductCategory> subtree = safeList(productCategoryMapper.selectSubtree(command.getCategoryId()));
        if (subtree.isEmpty()) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(productCategory)
                .deleteChildren(command.isDeleteChildren())
                .directChildren(directChildren)
                .targetParentChildren(targetParentChildren)
                .subtree(subtree)
                .build();
        for (CategoryDeleteChecker categoryDeleteChecker : safeList(categoryDeleteCheckers)) {
            categoryDeleteChecker.check(context);
        }

        List<Long> subtreeIds = subtree.stream().map(ProductCategory::getId).toList();
        if (command.isDeleteChildren()) {
            ensureAffected(productCategoryMapper.batchLogicDelete(subtreeIds), subtreeIds.size(),
                    ProductErrorCode.CATEGORY_DELETE_FAILED, "逻辑删除类目子树");
            log.info("级联删除类目成功: categoryId={}, affected={}", command.getCategoryId(), subtreeIds.size());
            return;
        }

        List<Long> childIds = directChildren.stream().map(ProductCategory::getId).toList();
        if (!childIds.isEmpty()) {
            try {
                ensureAffected(productCategoryMapper.batchUpdateParentId(childIds, productCategory.getParentId()), childIds.size(),
                        ProductErrorCode.CATEGORY_REPARENT_FAILED, "上提直接子类目");
            } catch (DuplicateKeyException e) {
                throw new BizException(ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE);
            }
        }

        List<Long> descendantIds = subtreeIds.stream()
                .filter(id -> !id.equals(command.getCategoryId()))
                .toList();
        if (!descendantIds.isEmpty()) {
            ensureAffected(productCategoryMapper.batchDecreaseLevel(descendantIds), descendantIds.size(),
                    ProductErrorCode.CATEGORY_LEVEL_DECREASE_FAILED, "调整后代类目层级");
        }
        ensureAffected(productCategoryMapper.batchLogicDelete(List.of(command.getCategoryId())), 1,
                ProductErrorCode.CATEGORY_DELETE_FAILED, "逻辑删除当前类目");
        log.info("删除并上提类目成功: categoryId={}, reparented={}, levelAdjusted={}",
                command.getCategoryId(), childIds.size(), descendantIds.size());
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void move(Long id, MoveCategoryDTO request) {
        Long targetParentId = request.getTargetParentId();
        Long operatorId = SYSTEM_OPERATOR_ID;

        log.info("开始移动类目: id={}, targetParentId={}", id, targetParentId);

        // ========== Step 1: 查询当前类目 ==========

        ProductCategory productCategory = productCategoryMapper.selectById(id);
        if (productCategory == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        // ========== Step 2: 查询目标父类目 ==========

        ProductCategory targetParent = null;
        if (targetParentId != 0) {
            targetParent = productCategoryMapper.selectById(targetParentId);
            if (targetParent == null) {
                throw new BizException(ProductErrorCode.CATEGORY_PARENT_NOT_FOUND);
            }
            if (CategoryStatus.DISABLED.matches(targetParent.getStatus())) {
                throw new BizException(ProductErrorCode.CATEGORY_PARENT_DISABLED);
            }
        }

        // ========== Step 3: 移动合法性校验 ==========
        Long currentParentId = productCategory.getParentId();
        if (targetParentId.equals(currentParentId)) {
            throw new BizException(ProductErrorCode.CATEGORY_PARENT_UNCHANGED);
        }

        List<ProductCategory> subtree = productCategoryMapper.selectSubtree(id);
        if (!CollectionUtils.isEmpty(subtree)) {
            List<Long> descendantIds = subtree.stream()
                    .map(ProductCategory::getId)
                    .collect(Collectors.toList());
            if (descendantIds.contains(targetParentId)) {
                throw new BizException(ProductErrorCode.CATEGORY_MOVE_TO_DESCENDANT);
            }
        }

        // ========== Step 4: 检查目标父类目下是否已有同名类目 ==========
        boolean exists = productCategoryMapper.existsByNameAndParent(targetParentId, productCategory.getName(), id);
        if (exists) {
            throw new BizException(ProductErrorCode.CATEGORY_NAME_DUPLICATE);
        }

        // ========== Step 5: 计算层级变化 ==========
        int targetLevel = (targetParentId == 0L) ? 0 : targetParent.getLevel();
        int currentLevel = productCategory.getLevel();
        int delta = targetLevel - currentLevel + 1;

        // ========== Step 6: 检查移动后最大层级 ==========
        if (!CollectionUtils.isEmpty(subtree)) {
            int maxDepth = subtree.
                    stream().
                    mapToInt(ProductCategory::getLevel).
                    max().
                    orElse(currentLevel) - currentLevel + 1;
            int newMaxDepth = maxDepth + targetLevel;
            if (newMaxDepth > MAX_LEVEL) {
                throw new BizException(ProductErrorCode.CATEGORY_LEVEL_EXCEEDED);
            }
        }

        // ========== Step 7: 执行更新 ==========
        int parentUpdated = productCategoryMapper.updateParentId(id, targetParentId, operatorId);
        if (parentUpdated != 1) {
            throw new BizException(ProductErrorCode.CATEGORY_MOVE_FAILED,
                    "更新父类目失败，期望影响1行，实际影响" + parentUpdated + "行");
        }
        log.debug("更新父类目成功: id={}, targetParentId={}", id, targetParentId);

        //  批量更新层级（delta != 0 时才执行）
        if(delta!=0){
            List<Long> allIds = new ArrayList<>();
            if(!CollectionUtils.isEmpty(subtree)) {
                List<Long> descendantIds = subtree.stream()
                        .map(ProductCategory::getId).collect(Collectors.toList());
                allIds.addAll(descendantIds);
            }
            int batchUpdateLevel = productCategoryMapper.batchUpdateLevel(allIds, delta, operatorId);
            if (batchUpdateLevel != allIds.size()) {
                throw new BizException(ProductErrorCode.CATEGORY_MOVE_FAILED,String.format("更新层级失败，期望影响 %d 行，实际影响 %d 行",
                        allIds.size(), batchUpdateLevel));
            }
            log.debug("更新层级成功: 影响 {} 行, delta={}", batchUpdateLevel, delta);
        }else {
            log.debug("delta=0，跳过层级更新");
        }
        log.info("移动类目成功: id={}, 原父类目={}, 新父类目={}, delta={}",
                id, currentParentId, targetParentId, delta);
    }


    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private void ensureAffected(int actual, int expected, ProductErrorCode errorCode, String operation) {
        if (actual != expected) {
            throw new BizException(errorCode,
                    String.format("%s失败，期望影响 %d 行，实际影响 %d 行", operation, expected, actual));
        }
    }

    private List<CategoryTreeVO> buildChildren(Long parentId, Map<Long, List<ProductCategory>> parentMap, boolean frontend, boolean parentVisibleInFront) {
        List<ProductCategory> children = parentMap.getOrDefault(parentId, Collections.emptyList());
        List<CategoryTreeVO> result = new ArrayList<>();
        for (ProductCategory category : children) {
            //前台模式，禁用节点直接跳过，不递归子节点
            boolean currentVisible = parentVisibleInFront && CategoryStatus.ENABLED.matches(category.getStatus());

            // ✅ 前台模式：不可见节点直接跳过，不递归其子孙
            if (frontend && !currentVisible) {
                continue;
            }
            CategoryTreeVO treeVO = categoryConverter.toTreeVO(category);
            // 递归构建子节点
            // 前台模式：只有当前节点启用才会走到这里，子节点继续按规则过滤
            // 后台模式：不过滤，全部返回
            List<CategoryTreeVO> childrenVOS = buildChildren(category.getId(), parentMap, frontend, currentVisible);
            // ✅ 叶子节点返回空数组，不返回 null
            treeVO.setChildren(childrenVOS != null ? childrenVOS : Collections.emptyList());
            // ✅ 后台模式：标记该节点在前台是否可见
            if (!frontend) {
                treeVO.setVisibleInFront(currentVisible);
            }
            result.add(treeVO);
        }
        return result;
    }


}
