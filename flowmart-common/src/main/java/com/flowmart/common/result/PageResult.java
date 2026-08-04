package com.flowmart.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页返回结构。
 * <p>
 * 刻意不直接返回 MyBatis-Plus 的 {@code IPage} —— 那会把 ORM 细节泄露到 API 契约里，
 * 将来换 ORM 或改成 ES 查询，前端就得跟着改。这是分层隔离的一个具体例子。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Schema(description = "当前页码", example = "1")
    private long pageNum;

    @Schema(description = "每页条数", example = "20")
    private long pageSize;

    @Schema(description = "总记录数", example = "137")
    private long total;

    @Schema(description = "当前页数据")
    private List<T> records;

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(pageNum, pageSize, 0L, Collections.emptyList());
    }

    /** 直接由 IPage 转换（entity 与返回对象同类型时） */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    /** 由 IPage 转换并做 entity -> VO 映射，这是最常用的重载 */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        List<T> records = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }
}
