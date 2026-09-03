package com.flowmart.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveCategoryDTO {
    @NotNull
    @Min(0)
    private Long targetParentId;
}
