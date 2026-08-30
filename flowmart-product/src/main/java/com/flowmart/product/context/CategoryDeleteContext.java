package com.flowmart.product.context;

import com.flowmart.product.entity.ProductCategory;

import lombok.Builder;
import lombok.Data;
import java.util.List;


@Data
@Builder
public class CategoryDeleteContext {
    private final ProductCategory category;
    private final boolean deleteChildren;
    private final List<ProductCategory> directChildren;
    private final List<ProductCategory> subtree;
    private final List<ProductCategory> targetParentChildren;

}
