package com.flowmart.product.vo;

import com.flowmart.product.enums.CategoryStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class CategoryDetailVO {
    private Long id;
    private Long parentId;
    private String name;
    private String iconUrl;
    private Integer level;
    private Integer sortNo;
    private Integer status;

    /**
     * 状态文本（前端展示用）
     */
    private String statusText;
    /**
     * 格式化后的创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 格式化后的更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 父类目名称（关联查询后填充）
     */
    private String parentName;

    /**
     * 是否有子类目
     */
    private Boolean hasChildren;
    /**
     * 构造状态文本
     */
    public String getStatusText() {
        return CategoryStatus.values()[status].name();
    }

}
