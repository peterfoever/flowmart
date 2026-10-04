package com.flowmart.product.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuUpdateDTO {
    @NotNull(message = "价格必填")
    @DecimalMin(value = "0.00", message = "价格不能为负数")
    @Digits(integer = 8, fraction = 2, message = "价格最多8位整数、2位小数")
    private BigDecimal price;

    @NotBlank(message = "图片不能为空")
    @Size(max = 512, message = "图片URL不能超过512个字符")
    private String imageUrl;

    @NotNull(message = "版本号必填")
    @Min(value = 0, message = "版本号不能为负数")
    private Integer version;
}
