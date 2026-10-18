package com.flowmart.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SpuUnpublishDTO {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能为负数")
    private Integer version;
}
