package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.entity.ProductCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {
    /**
     * 插入类目
     */
    int insert(ProductCategory entity);

    /**
     * 根据 ID 查询（未删除）
     */
    ProductCategory selectById(@Param("id") Long id);
    /**
     * 检查同级是否存在同名类目
     *
     * @param parentId  父类目 ID
     * @param name      类目名称
     * @param excludeId 排除的类目 ID（更新时使用，新增时传 null）
     * @return true 表示存在
     */
    boolean existsByNameAndParent(
            @Param("parentId") Long parentId,
            @Param("name") String name,
            @Param("excludeId") Long excludeId
    );

    List<ProductCategory> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 查询所有未删除类目，按 sort_no ASC, id ASC 排序
     */
    List<ProductCategory> selectAllUndeletedOrdered();

    /**
     * 递归查询子树类别
     * @param rootId 待删除类目id
     * @return
     */
    List<ProductCategory> selectSubtree(
            @Param("rootId") Long rootId
    );

    /**
     * 批量逻辑删除（按 ID 列表）
     *
     * @param ids       待删除的类目 ID 列表
     * @return 实际影响行数
     */
    int batchLogicDelete(@Param("ids") List<Long> ids

                         );

    /**
     * 批量更新父类目 ID（子类目上提）
     *
     * @param childIds       待上提的子类目 ID 列表
     * @param targetParentId 目标父类目 ID

     * @return 实际影响行数
     */
    int batchUpdateParentId(@Param("childIds") List<Long> childIds,
                            @Param("targetParentId") Long targetParentId);

    /**
     * 批量层级减 1（后代类目上提后层级收缩）
     *
     * @param descendantIds 后代类目 ID 列表（含直接子类目）

     * @return 实际影响行数
     */
    int batchDecreaseLevel(@Param("descendantIds") List<Long> descendantIds);
}
