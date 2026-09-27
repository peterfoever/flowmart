package com.flowmart.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateSpuDTO {
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称不能超过128个字符")
    private String name;

    @NotNull(message = "类目ID不能为空")
    @Min(value = 1, message = "类目ID必须为正数")
    private Long categoryId;

    /** 品牌ID，允许为 null（无品牌） */
    @Min(value = 1, message = "品牌ID必须为正数")
    private Long brandId;

    @NotBlank(message = "主图不能为空")
    @Size(max = 512, message = "主图URL不能超过512个字符")
    private String mainImageUrl;

    /** 轮播图，必须传 空数组表示清空*/
    @NotNull(message = "轮播图不能为空")
    @Size(max = 7, message = "轮播图最多7张")
    private List<@NotBlank @Size(max = 512) String> carouselImages;

    /** 规格列表，可为 null（等同 []） */
    @NotNull
    @Size(max=3)
    private List<@NotNull @Valid SpecDTO> specs;

    @Size(max = 5000,message = "商品详情最多为5000字符")
    private String description;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能为负数")
    private Integer version;
}
