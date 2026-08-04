package com.flowmart.common.exception;

/**
 * 错误码契约。
 * <p>
 * 各业务模块自行实现该接口定义自己的错误码枚举，避免所有错误码挤在一个巨型枚举里。
 * <p>
 * 编码规则（5 位）：{@code A BB CC}
 * <ul>
 *   <li>A  —— 模块号：1 通用 / 2 商品 / 3 库存 / 4 订单 / 5 履约 / 6 结算</li>
 *   <li>BB —— 子域号，模块内自行划分</li>
 *   <li>CC —— 错误序号</li>
 * </ul>
 * 例：{@code 20101} = 商品模块(2) 类目子域(01) 第 01 个错误。
 */
public interface ErrorCode {

    int getCode();

    String getMessage();
}
