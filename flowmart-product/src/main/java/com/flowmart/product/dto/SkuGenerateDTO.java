package com.flowmart.product.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SkuGenerateDTO {
    @NotEmpty(message = "SKU 列表不能为空")
    @Size(max = 1000, message = "单个 SPU 最多 1000 个 SKU")
    private List<SkuGenerateItemDTO> items;
}
