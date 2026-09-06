package com.flowmart.product.vo;

import lombok.Data;

import java.util.List;

/**
 * 类目树 VO（用于前端级联选择器）
 */
@Data
public class CategoryTreeVO {
    private Long id;
    private Long parentId;
    private String name;
    private String iconUrl;
    private Integer level;
    private Integer sortNo;
    private Integer status;

    /**
     * 子类目列表（递归）
     */
    private List<CategoryTreeVO> children;

    /**
     * 是否叶子节点
     */
    public Boolean getIsLeaf() {
        return children == null || children.isEmpty();
    }

    /**
     * 后台树专用：标记该节点在前台是否可见
     * <p>
     * true = 整条祖先链路均为启用，前台会展示
     * false = 自身或祖先被禁用，前台不可见
     */
    private Boolean visibleInFront;
}
