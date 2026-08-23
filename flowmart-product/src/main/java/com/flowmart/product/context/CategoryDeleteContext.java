package com.flowmart.product.context;

import com.flowmart.product.entity.ProductCategory;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
public class CategoryDeleteContext {
    private ProductCategory category;
    private boolean deleteChildren;
    private List<ProductCategory> directChildren;
    private List<ProductCategory> subtree;
    private List<ProductCategory> targetParentChildren;

}
