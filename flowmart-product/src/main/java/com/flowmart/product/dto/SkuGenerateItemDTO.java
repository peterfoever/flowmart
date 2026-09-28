package com.flowmart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class SkuGenerateItemDTO {
    /** 规格组合；无规格商品传空 Map 或 null。 */
    private Map<String, String> specValues;

    @NotNull(message = "初始价格必填")
    @DecimalMin(value = "0.00", message = "价格不能为负数")
    @Digits(integer = 16, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "市场价不能为负数")
    @Digits(integer = 16, fraction = 2, message = "市场价最多两位小数")
    private BigDecimal marketPrice;

    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;
}
