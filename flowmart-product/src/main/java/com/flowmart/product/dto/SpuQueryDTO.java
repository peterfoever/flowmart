package com.flowmart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "SPU 分页查询请求")
public class SpuQueryDTO {

    @Schema(description = "关键词，只匹配 SPU 名称，trim 后包含匹配", example = "手机")
    private String keyword;

    @Schema(description = "SPU 编码，精确匹配", example = "SPU20261001")
    private String spuCode;

    @Schema(description = "类目 ID，包含自身及所有后代；<=0 非法", example = "10")
    private Long categoryId;

    @Schema(description = "品牌 ID，指定品牌；<=0 非法", example = "1001")
    private Long brandId;

    @Schema(description = "是否筛选无品牌商品；与 brandId 同时传时非法", example = "false")
    private Boolean noBrand;

    @Schema(description = "创建时间起，格式 yyyy-MM-dd HH:mm:ss，左闭", example = "2026-10-07 00:00:00")
    private String createdFrom;

    @Schema(description = "创建时间止，格式 yyyy-MM-dd HH:mm:ss，右开", example = "2026-10-08 00:00:00")
    private String createdTo;

    @Schema(description = "页码，默认 1，>=1", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数，默认 20，范围 1~200", example = "20")
    private Integer pageSize;

    // ── 分页默认值 ────────────────────────────────────────────────
    public int resolvePageNum() {
        return pageNum == null ? 1 : pageNum;
    }

    public int resolvePageSize() {
        return pageSize == null ? 20 : pageSize;
    }
}
