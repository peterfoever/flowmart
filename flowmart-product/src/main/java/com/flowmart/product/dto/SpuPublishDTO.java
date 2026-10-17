package com.flowmart.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 上架使用客户端读取的版本，防止旧页面操作覆盖新状态。 */
@Data
public class SpuPublishDTO {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能为负数")
    private Integer version;
}
