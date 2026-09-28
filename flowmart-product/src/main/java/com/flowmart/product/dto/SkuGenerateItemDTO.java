package com.flowmart.product.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class SkuGenerateItemDTO {
    /** 规格组合；无规格商品传空 Map 或 null。 */
    private Map<String, String> specValues;

    @NotNull(message = "初始价格必填")
    @DecimalMin(value = "0.00", message = "价格不能为负数")
    @Digits(integer = 8, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    @NotBlank(message = "图片不能为空")
    @Size(max = 512, message = "主图URL不能超过512个字符")
    private String imageUrl;
}
