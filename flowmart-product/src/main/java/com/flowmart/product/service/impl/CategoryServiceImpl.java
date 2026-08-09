package com.flowmart.product.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.lang.generator.SnowflakeGenerator;
import com.flowmart.common.exception.BizException;
import com.flowmart.product.convert.CategoryConverter;
import com.flowmart.product.dto.CreateCategoryDTO;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.CategoryStatus;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.service.CategoryService;

import com.flowmart.product.vo.CategoryDetailVO;
import com.flowmart.product.vo.CategoryTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private final ProductCategoryMapper productCategoryMapper;
    private final CategoryConverter categoryConverter;
    /** 类目最大层级 */
    private static final int MAX_LEVEL = 5;

    public CategoryServiceImpl(ProductCategoryMapper productCategoryMapper, CategoryConverter categoryConverter) {
        this.productCategoryMapper = productCategoryMapper;
        this.categoryConverter = categoryConverter;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long creat(CreateCategoryDTO categoryDTO) {
        Integer level;
        if (categoryDTO.getParentId() == null || categoryDTO.getParentId() == 0) {
            // 一级类目
            level = 0;
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

        // 审计字段手动填充（如果没有拦截器）
        Long currentUserId = getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedBy(currentUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedBy(currentUserId);
        entity.setUpdatedAt(now);
        entity.setDeleted(0L);
        entity.setVersion(0);
        int rows = productCategoryMapper.insert(entity);
        try {
            if (rows != 1) {
                log.error("插入类目失败: rows={}, entity={}", rows, entity);
                throw new BizException(ProductErrorCode.CATEGORY_CREATE_FAILED);
            }
        } catch (BizException e) {
            // 并发兜底：唯一索引 uk_parent_name_deleted 触发
            // 这是数据库层面的最后一道防线
            log.warn("并发创建同名类目冲突：parentId={}, name={}", categoryDTO.getParentId(), categoryDTO.getName(),e);
            throw new BizException(ProductErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        // ========== 6. 日志 ==========
        log.info("创建类目成功: categoryId={}, parentId={}, name={}, level={}",
                entity.getId(), entity.getParentId(), entity.getName(), entity.getLevel());

        // ========== 7. 返回 ==========
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CategoryTreeVO> listChildren(Long parentId) {
        List<ProductCategory> productCategories = productCategoryMapper.selectByParentId(parentId);
        if (productCategories == null || productCategories.isEmpty()) {
            log.warn("该类目下不存在子类目，id={}", parentId);
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        return categoryConverter.toTreeVOList(productCategories);

    }
    /**
     * 获取当前用户 ID
     * <p>
     * TODO: 从 ThreadLocal 或 SecurityContext 中获取真实用户
     */
    private Long getCurrentUserId() {
        // 临时兜底，后续接入登录上下文
        return 0L;
    }
}
