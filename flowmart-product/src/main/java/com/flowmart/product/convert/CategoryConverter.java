package com.flowmart.product.convert;

import com.flowmart.product.dto.CreateCategoryDTO;
import com.flowmart.product.dto.UpdateCategoryDTO;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.vo.CategoryDetailVO;
import com.flowmart.product.vo.CategoryTreeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 商品类目对象转换器。
 *
 * <p>level 由服务层根据父类目计算；children、parentName、hasChildren 由查询/组树逻辑填充，
 * 均不能从接口入参或数据库实体自动覆盖。</p>
 */
@Mapper(componentModel = "spring")
public interface CategoryConverter {

    /** 将新增请求转换为待持久化实体；后端负责生成 ID 与计算层级。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    ProductCategory toEntity(CreateCategoryDTO dto);

    /** 将允许编辑的字段覆盖到已有实体；parentId 必须由移动接口单独处理。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parentId", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateCategoryDTO dto, @MappingTarget ProductCategory entity);

    /** 实体转换为详情响应；额外展示字段由 Service 查询后补充。 */
    @Mapping(target = "statusText", ignore = true)
    @Mapping(target = "parentName", ignore = true)
    @Mapping(target = "hasChildren", ignore = true)
    CategoryDetailVO toDetailVO(ProductCategory entity);

    /** 实体转换为树节点；children 由 Service 一次全量查询后在内存中组装。 */
    @Mapping(target = "children", ignore = true)
    CategoryTreeVO toTreeVO(ProductCategory entity);

    List<CategoryTreeVO> toTreeVOList(List<ProductCategory> entities);
}
