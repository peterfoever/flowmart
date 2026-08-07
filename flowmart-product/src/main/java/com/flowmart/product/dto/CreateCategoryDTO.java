package com.flowmart.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryDTO {
    @NotNull(message = "父类目ID不能为空")
    private Long parentId;

    @NotBlank(message = "类目名称不能为空")
    @Size(max = 64, message = "类目名称长度不能超过64个字符")
    private String name;

    @Size(max = 256, message = "图标URL长度不能超过256个字符")
    private String iconUrl;


    private Integer sortNo = 0;

    private Integer status = 1;
}
