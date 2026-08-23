package com.flowmart.product.command;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class DeleteCategoryCommand {
    private Long categoryId;
    private boolean deleteChildren;
    private Long currentUserId;
}
