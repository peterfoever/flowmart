package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.dto.SpuQueryDTO;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.vo.SpuListVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductSpuMapper extends BaseMapper<ProductSpu> {

    /**
     * 更新 SPU 草稿（带乐观锁）
     * <li>WHERE 带 status = 0，防止修改过程中被上架</li>
     *
     * @return
     */
    int updateDraftById(@Param("id") Long id,
                        @Param("version") Integer version,
                        @Param("name") String name,
                        @Param("categoryId") Long categoryId,
                        @Param("brandId") Long brandId,
                        @Param("mainImageUrl") String mainImageUrl,
                        @Param("carouselImages") List<String> carouselImages,
                        @Param("specs") List<SpecDTO> specs,
                        @Param("description") String description,
                        @Param("updatedBy") Long updatedBy);

    /**
     * 判断指定品牌下是否存在未删除的 SPU
     * @param brandId
     * @return
     */
    boolean existsByBrandId(@Param("brandId") Long brandId);

    /**
     * 判断指定类目集合下是否存在未删除的 SPU

     */
    boolean existsByCategoryIdsAndDeleted(@Param("categoryIds") List<Long> categoryIds);

    /**
     * 判断指定类目下、引用了指定品牌集合的未删除 SPU 是否存在
     * <p>
     * 用于替换绑定场景：只检查"将被移除"的品牌组合

     */
    boolean existsByCategoryIdAndBrandIds(@Param("categoryId") Long categoryId,
                                          @Param("brandIds") List<Long> brandIds);

    // ProductSpuMapper：锁定并读取有效 SPU
    ProductSpu selectByIdForUpdate(@Param("id") Long id);

    /** 分页查询 SPU 列表，JOIN 类目、品牌仅用于展示。 */
    List<SpuListVO> selectSpuPage(@Param("q") SpuQueryDTO q,
                                  @Param("keywordPattern") String keywordPattern,
                                  @Param("categoryIds") List<Long> categoryIds,
                                  @Param("offset") long offset);

    /** 与列表共用 WHERE 条件的 COUNT，只查 SPU，不 JOIN。 */
    long countSpu(@Param("q") SpuQueryDTO q,
                  @Param("keywordPattern") String keywordPattern,
                  @Param("categoryIds") List<Long> categoryIds);
}
