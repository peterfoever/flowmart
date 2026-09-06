package com.flowmart.product.command;

import lombok.Data;

@Data
public class DeleteCategoryCommand {
    private Long categoryId;
    private boolean deleteChildren;
    private Long currentUserId;
}
