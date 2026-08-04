# 编码规范

> 规范的价值不在「好看」，而在于**减少沟通成本**和**避免已知事故**。
> 下面每一条后面都有「为什么」——不理解为什么的规范，你迟早会破例。

## 一、分层职责

```
Controller  →  Service  →  Mapper
```

| 层 | 只做 | 绝对不做 |
|---|---|---|
| Controller | 参数接收、校验触发、调用 Service、包装 `R<T>` | 业务判断、事务、直接调 Mapper |
| Service | 业务编排、事务边界、业务校验 | 处理 HTTP 相关的东西（HttpServletRequest 不该出现在这层） |
| Mapper | SQL | 任何业务逻辑 |

**为什么 Controller 不能写业务逻辑**：写在 Controller 的逻辑无法被其他 Service 复用，
也无法被单元测试直接覆盖。等你需要加一个定时任务复用同样的逻辑时，只能复制粘贴。

## 二、对象分层

| 类型 | 后缀 | 用途 | 所在包 |
|---|---|---|---|
| Entity | 无 / `DO` | 与数据库表一一对应 | `entity` |
| DTO | `Request` / `Command` / `Query` | 接口入参 | `dto` |
| VO | `VO` | 接口出参 | `vo` |

**转换统一用 MapStruct**，不要手写 `BeanUtils.copyProperties`：
前者编译期生成代码、类型不匹配时直接编译失败；后者是运行期反射，字段改名了你要到线上才发现。

## 三、异常与错误码

```java
// ✅ 可预期的业务分支
if (category.hasChildren()) {
    throw new BizException(ProductErrorCode.CATEGORY_HAS_CHILDREN);
}

// ✅ 需要携带上下文时
BizException.check(stock >= qty, InventoryErrorCode.STOCK_NOT_ENOUGH,
        String.format("库存不足，可用 %d，申请 %d", stock, qty));

// ❌ 不要裸抛
throw new RuntimeException("类目有子级");   // 前端拿到的是"系统繁忙"，用户一脸茫然
```

**错误提示要写给人看**：`"操作失败"` 等于没说。`"该类目下还有 3 个子类目，请先删除子类目"` 才有用。

## 四、事务

```java
// ✅ 事务边界打在 Service 的 public 方法上
@Transactional(rollbackFor = Exception.class)
public void createCategory(CategoryCreateRequest request) { ... }
```

**四个高频事务失效场景**（W4 会专门做实验，先记住）：
1. `@Transactional` 加在 `private`/`final` 方法上 → 代理无法织入
2. 同类内部调用 `this.methodB()` → 不走代理，事务不生效
3. 异常被 `try-catch` 吞了没往外抛 → Spring 感知不到，不回滚
4. 默认只回滚 `RuntimeException`，抛受检异常不回滚 → 永远写 `rollbackFor = Exception.class`

**事务里不要做这些**（会长时间占着数据库连接，高并发下直接打满连接池）：
- 调用外部 HTTP 接口
- 发 MQ 消息
- 大文件读写
- `Thread.sleep`

## 五、日志

```java
// ✅ 占位符 + 关键业务 ID
log.info("创建类目成功, categoryId={}, parentId={}, name={}", id, parentId, name);

// ❌ 字符串拼接：即使日志级别没开，拼接也已经发生了，白白消耗 CPU
log.debug("创建类目: " + JSON.toJSONString(request));

// ❌ 打敏感信息：手机号、身份证、密码、Token 一律脱敏
log.info("用户登录, phone={}, password={}", phone, password);
```

**日志级别怎么选**：
- `ERROR` —— 需要有人半夜起来处理的。滥用 ERROR 会让告警变成狼来了
- `WARN` —— 不正常但系统能自愈的（重试成功、降级生效）
- `INFO` —— 关键业务动作（下单、扣库存、发货），要能靠它还原业务流程
- `DEBUG` —— 排查用的细节，生产环境默认关闭

## 六、数据库

1. **所有 DDL 走 Flyway**，禁止手工连生产改表
2. **所有业务表必须有** `created_by/created_at/updated_by/updated_at/deleted/version`
3. **不用外键约束** —— 分库分表时无法维护，且会带来意外的锁
4. **禁止 `SELECT *`** —— 加字段时会把大字段一起捞出来
5. **唯一索引必须带 `deleted`** —— 否则软删后无法复用同一个编码
6. **批量操作必须分批**，单批不超过 500 条
7. **`WHERE` 条件的字段必须有索引**，写完 SQL 自己 `EXPLAIN` 一遍

## 七、命名

```java
// 类：大驼峰，见名知意
CategoryService  CategoryCreateRequest  ProductErrorCode

// 方法：动词开头
createCategory()  listCategoryTree()  checkCircularReference()

// 布尔：is/has/can 开头
boolean hasChildren;  boolean canDelete;

// 常量：全大写下划线
private static final int MAX_CATEGORY_LEVEL = 3;

// ❌ 禁止的命名
List<Category> list1, list2;      // 一周后你自己都不知道谁是谁
Object obj;  String str;  int i2;
```

**禁止魔法值**：
```java
// ❌
if (category.getStatus() == 1) { ... }

// ✅
if (CategoryStatus.ENABLED.matches(category.getStatus())) { ... }
```

## 八、注释

**注释写「为什么」，不写「是什么」**：

```java
// ❌ 这行注释等于没写，代码本身就说了这件事
// 设置状态为启用
category.setStatus(1);

// ✅ 解释了代码里看不出来的意图
// 新建类目默认禁用，需要运营在后台确认信息无误后手动启用 ——
// 这是 2026-07 那次前台展示了半成品类目的事故后加的约束
category.setStatus(CategoryStatus.DISABLED.getCode());
```

复杂业务方法必须写 Javadoc，说明：做什么、关键参数含义、会抛什么业务异常。

## 九、测试

单元测试**至少覆盖**：
1. 正常路径
2. 业务异常路径（每个 `throw new BizException` 都该有对应用例）
3. 边界值（空集合、null、最大层级、最大长度）

```java
@Test
@DisplayName("移动类目到自己的子类目下时应当被拒绝")
void moveCategory_toOwnDescendant_shouldThrow() {
    // given / when / then 三段式，测试名要说清楚"什么场景下期望什么结果"
}
```

测试名用 `@DisplayName` 写中文。测试失败时，CI 的报错信息应该让你不看代码就知道哪出问题了。
