package com.flowmart.product.dto;

import com.flowmart.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "SPU 分页查询请求")
public class SpuQueryDTO extends PageQuery {

    @Schema(description = "关键词，只匹配 SPU 名称，trim 后包含匹配", example = "手机")
    @Size(max = 128)
    private String keyword;

    @Schema(description = "SPU 编码，精确匹配", example = "SPU20261001")
    @Size(max = 64)
    private String spuCode;

    @Schema(description = "类目 ID，包含自身及所有后代；<=0 非法", example = "10")
    @Min(1)
    private Long categoryId;

    @Schema(description = "品牌 ID，指定品牌；<=0 非法", example = "1001")
    @Min(1)
    private Long brandId;

    @Schema(description = "状态")
    @Min(0)
    @Max(2)
    private Integer status;

    @Schema(description = "是否筛选无品牌商品；与 brandId 同时传时非法", example = "false")
    private Boolean noBrand;

    @Schema(description = "创建时间起，格式 yyyy-MM-dd HH:mm:ss，左闭", example = "2026-10-07 00:00:00")
    private String createdFrom;

    @Schema(description = "创建时间止，格式 yyyy-MM-dd HH:mm:ss，右开", example = "2026-10-08 00:00:00")
    private String createdTo;

    @Override
    @NotNull(message = "pageNum 不能为空")
    public Integer getPageNum() {
        return super.getPageNum();
    }

    @Override
    @NotNull(message = "pageSize 不能为空")
    public Integer getPageSize() {
        return super.getPageSize();
    }

}
