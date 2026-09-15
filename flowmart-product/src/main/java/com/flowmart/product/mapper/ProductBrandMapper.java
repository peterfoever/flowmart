package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.dto.BrandQueryDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.entity.ProductCategoryBrand;
import com.flowmart.product.vo.CategoryBrandVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductBrandMapper extends BaseMapper<ProductBrand> {

    /** 绑定前锁定未删除品牌，需在事务内调用并将 IDs 升序分批传入。 */
    List<ProductBrand> selectByIdsForUpdate(@Param("ids") List<Long> ids);

    /** 检查其他未删除品牌是否占用该名称，供修改品牌使用。 */
    boolean existsByNameExcludingId(@Param("name") String name,
                                  @Param("excludeId") Long excludeId);

    /**
     *
     * @param name 品牌名称
     * @param deleted 逻辑删除
     * @return
     */
    boolean existsByNameAndDeleted(@Param("name") String name,
                                   @Param("deleted") Long deleted);

    /**
     * 分页查询品牌
     *
     * @param query  查询条件
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 品牌列表
     */
    List<ProductBrand> selectBrandPage(@Param("query") BrandQueryDTO query,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    /**
     * 统计符合条件的品牌数量
     *
     * @param query 查询条件
     * @return 总数
     */
    long countByQuery(@Param("query") BrandQueryDTO query);

    /**
     * 逻辑删除类目下所有未删除的绑定
     * <p>
     * 注意：
     * - 仅处理 deleted = 0 的记录
     * - 使用 deleted = id 保证每行删除值唯一
     * - 返回 0 是正常情况（首次绑定、重复清空）
     */

    int logicDeleteByCategoryId(@Param("categoryId") Long categoryId,
                                @Param("updatedBy") Long updatedBy);

    /**
     * 批量查询未删除的品牌
     * @param ids
     * @param deleted
     * @return
     */
    List<ProductBrand> selectByIdsAndDeleted(@Param("ids") List<Long> ids,
                                             @Param("deleted") Long deleted);
    /**
     * 批量插入绑定
     */
    int batchInsert(@Param("bindings") List<ProductCategoryBrand> bindings);

    /**
     * 查询类目当前绑定的品牌列表
     * @param categoryId
     * @return
     */
    List<CategoryBrandVO> selectBrandsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 查询品牌并加行锁
     * @param id
     * @return
     */
    ProductBrand selectByIdForUpdate(@Param("id") Long id);

    /**
     * 统计品牌是否被类目绑定
     *
     * @param brandId 品牌 ID
     * @return 绑定数量
     */
    boolean existCategoryBindings(@Param("brandId") Long brandId);

    /**
     * 删除品牌时显式设置删除标记
     * @param id
     * @param version
     * @param updatedBy
     * @return
     */
    int logicDeleteById(@Param("id") Long id,
                        @Param("version") Integer version,
                        @Param("updatedBy") Long updatedBy);
}
