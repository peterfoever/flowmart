package com.flowmart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuGenerateDTO {
    @NotNull(message = "初始价格必填")
    @DecimalMin(value = "0.00", message = "价格不能为负数")
    @Digits(integer = 8, fraction = 2, message = "价格最多8位整数、2位小数")
    private BigDecimal price;

    /** 未传或空白时，由 Service 复制生成时的 SPU 主图。规格组合也由 Service 生成。 */
    @Size(max = 512, message = "图片URL不能超过512个字符")
    private String imageUrl;
}
