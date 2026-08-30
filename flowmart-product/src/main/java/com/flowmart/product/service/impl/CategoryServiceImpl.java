package com.flowmart.product.service.impl;


import com.flowmart.common.exception.BizException;
import com.flowmart.product.checker.CategoryDeleteChecker;
import com.flowmart.product.command.DeleteCategoryCommand;
import com.flowmart.product.context.CategoryDeleteContext;
import com.flowmart.product.convert.CategoryConverter;
import com.flowmart.product.dto.CreateCategoryDTO;
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

    /**
     * 类目最大层级
     */
    private static final int MAX_LEVEL = 5;

    public CategoryServiceImpl(ProductCategoryMapper productCategoryMapper, CategoryConverter categoryConverter, CategoryDeleteChecker categoryDeleteChecker, List<CategoryDeleteChecker> categoryDeleteCheckers) {
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

        return buildChildren(0L, parentMap, true,true);
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
        return buildChildren(0L, parentMap, false,true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(DeleteCategoryCommand command) {
        ProductCategory productCategory = productCategoryMapper.selectById(command.getCategoryId());
        if (productCategory == null) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }


        List<ProductCategory> directChildren = productCategoryMapper.selectByParentId(command.getCategoryId());


        List<ProductCategory> targetParentChildren = productCategoryMapper.selectByParentId(productCategory.getParentId());

        List<ProductCategory> subtreeIds = productCategoryMapper.selectSubtree(command.getCategoryId());

        // ✅ 一次性构建 Context
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(productCategory)
                .deleteChildren(command.isDeleteChildren())
                .directChildren(directChildren)
                .targetParentChildren(targetParentChildren)
                .subtree(subtreeIds)
                .build();
        for (CategoryDeleteChecker categoryDeleteChecker : categoryDeleteCheckers) {
            categoryDeleteChecker.check(context);
        }
        if(command.isDeleteChildren()){
            log.info("开始删除类目，categoryId={}, deleteChildren={}", command.getCategoryId(), context);
            if (CollectionUtils.isEmpty(subtreeIds)) {
                log.debug("ID 列表为空，跳过批量更新");
                return;
            }
            int deleted = productCategoryMapper.batchLogicDelete(subtreeIds.stream().map(ProductCategory::getId).collect(Collectors.toList()));
            if (deleted != 1) {
                throw new BizException(ProductErrorCode.CATEGORY_DELETE_FAILED,
                        "删除当前类目失败，期望影响 1 行，实际影响 " + deleted + " 行");
            }

        }else {

            // ===== B2: 批量上提直接子类目 =====
            List<Long> childIds = directChildren.stream()
                    .map(ProductCategory::getId)
                    .collect(Collectors.toList());

            // ✅ 目标父类目 ID = category.getParentId()
            int reparentCount = productCategoryMapper.batchUpdateParentId(childIds, context.getCategory().getParentId());
            if (reparentCount != childIds.size()) {
                throw new BizException(ProductErrorCode.CATEGORY_REPARENT_FAILED,
                        String.format("期望上提 %d 个子类目，实际上提 %d 个", childIds.size(), reparentCount));
            }
            log.debug("上提直接子类目: childIds.size={}, targetParentId={}, affected={}",
                    childIds.size(), context.getCategory().getParentId(), reparentCount);


            // ===== B3: 查询所有后代（用于层级调整） =====
            // ✅ 注意：此时当前节点已被逻辑删除，selectSubtree 只查 deleted=0 的节点
            // 所以返回的是所有后代（不含当前节点）
            List<ProductCategory> subtree = productCategoryMapper.selectSubtree(command.getCategoryId());
            if (subtree == null || subtree.isEmpty()) {
                log.debug("无后代需要调整层级");
                return;
            }

            List<Long> descendantIds = subtree.stream()
                    .map(ProductCategory::getId)
                    .collect(Collectors.toList());

            // ✅ 验证：descendantIds 中不包含 categoryId
            if (descendantIds.contains(command.getCategoryId())) {
                log.warn("selectSubtree 返回了当前节点，请检查 SQL 的 WHERE deleted=0 条件");
                descendantIds.remove(command.getCategoryId());
            }

            // ===== B4: 批量层级减 1 =====
            int levelDecreased = productCategoryMapper.batchDecreaseLevel(descendantIds);
            if (levelDecreased != descendantIds.size()) {
                throw new BizException(ProductErrorCode.CATEGORY_LEVEL_DECREASE_FAILED,
                        String.format("期望调整 %d 个后代的层级，实际调整 %d 个", descendantIds.size(), levelDecreased));
            }

            log.info("删除并上提完成: 删除当前节点, 上提 {} 个直接子类目到 parent_id={}, 调整 {} 个后代的层级",
                    reparentCount, context.getCategory().getParentId(), levelDecreased);
        }
    }

    private List<CategoryTreeVO> buildChildren(Long parentId, Map<Long, List<ProductCategory>> parentMap, boolean frontend,boolean parentVisibleInFront) {
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
            List<CategoryTreeVO> childrenVOS = buildChildren(category.getId(), parentMap, frontend,currentVisible);
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
