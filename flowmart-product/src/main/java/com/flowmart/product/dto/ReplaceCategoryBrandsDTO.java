package com.flowmart.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * 全量替换类目-品牌绑定请求
 * <p>
 * 注意：
 * - 不使用 @NotEmpty：空数组是合法的"解除全部绑定"操作
 * - 使用 @NotNull：brandIds 本身不能为 null（区分"未传"和"传空数组"）
 * - 使用 @NotNull @Positive 对元素逐个校验，拒绝 null、0、负数
 */
@Data
public class ReplaceCategoryBrandsDTO {
    @NotNull(message = "品牌ID列表不能为null")
    private List<@NotNull(message = "品牌ID不能为null")
                 @Positive(message = "品牌ID必须为正数")Long> brandIds;
}
