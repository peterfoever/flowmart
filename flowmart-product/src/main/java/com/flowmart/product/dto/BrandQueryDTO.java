package com.flowmart.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandQueryDTO {

    @Size(max = 64)
    private String name;      // 模糊查询
    @Pattern(regexp = "^$|^[A-Z]$", message = "首字母必须为单个大写字母")
    private String initial;   // 首字母精确匹配
    @Min(0)
    @Max(1)
    private Integer status;   // 状态过滤

    @NotNull
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @NotNull
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Integer size = 20;
}
