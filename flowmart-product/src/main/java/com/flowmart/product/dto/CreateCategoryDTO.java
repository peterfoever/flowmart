package com.flowmart.product.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateCategoryDTO {
    @NotNull(message = "父类目ID不能为空")
    @Min(value = 0, message = "父类id最小为0")
    private Long parentId;

    @NotBlank(message = "类目名称不能为空")
    @Size(max = 64, message = "类目名称长度不能超过64个字符")
    private String name;

    @Size(max = 256, message = "图标URL长度不能超过256个字符")
    private String iconUrl;

    /**
     * 同级排序，越小越靠前
     */
    @Min(value = 0, message = "排序值不能为负数")
    private Integer sortNo = 0;

    @Min(value = 0, message = "类目状态只能为0或1")
    @Max(value = 1, message = "类目状态只能为0或1")
    @NotNull
    private Integer status = 1;
}
