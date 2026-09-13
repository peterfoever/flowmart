package com.flowmart.product.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateBrandDTO {
    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 64, message = "品牌名称不能超过64个字符")
    private String name;

    @Size(max = 256, message = "Logo URL不能超过256个字符")
    private String logoUrl;

    @NotBlank(message = "首字母不能为空")
    @Pattern(regexp = "^[A-Z]$", message = "首字母必须为单个大写字母")
    private String initial;

    @NotNull
    @Min(value = 0, message = "排序值不能为负数")
    private Integer sortNo;


}
