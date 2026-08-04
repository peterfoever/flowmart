package com.flowmart.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询入参基类，业务查询条件继承它。
 * <p>
 * {@code pageSize} 必须设上限。线上事故常见起因之一就是前端传了 pageSize=100000，
 * 一次查询把数据库和应用内存一起打满。
 */
@Data
@Schema(description = "分页查询参数")
public class PageQuery {

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "20", defaultValue = "20")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能大于 200")
    private Integer pageSize = 20;
}
