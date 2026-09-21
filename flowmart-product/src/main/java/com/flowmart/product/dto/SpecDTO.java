package com.flowmart.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;



import java.util.List;

@Data
public class SpecDTO {

    @NotBlank(message = "规格名不能为空")
    @Size(max = 32, message = "名称长度最大为32字符")
    private String name;

    @NotNull(message = "规格值列表不能为null")
    @Size(min = 1, max = 50, message = "规格值数量必须在1~50之间")
    private List<@NotBlank(message = "规格值不能为空")
                @Size(max = 64, message = "规格值不能超过64个字符")String> values;
}
