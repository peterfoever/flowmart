# FM-006 上架接口阶段验收

本阶段仅实现上架与重新上架；不代表完整 FM-006（包含下架）已经交付。

## 请求契约

```http
POST {{baseUrl}}/api/product/admin/spus/{{spuId}}/publish
Content-Type: application/json

{"version": 0}
```

`version` 必须使用详情接口当前返回的值，不能始终写死为 0。请求体是 JSON 对象，不是裸数字。
成功返回统一结构 `code=0, success=true`；状态限定 `DRAFT(0)` / `OFF_SHELF(2)` → `ON_SHELF(1)`。
重复上架返回业务错误，不再增加版本。所有操作人沿用项目当前的系统操作人 `0L`。

## 本轮自动化回归

- `SpuPublishServiceTest`：无品牌、默认 SKU、多规格、重新上架、1000 个组合边界、状态/版本冲突、类目/品牌有效性、价格/图片、规格内容与哈希一致性、影响行数异常。
- `SpuPublishControllerTest`：真实 MVC JSON 对象绑定、参数校验和业务错误封装。
- `SpuPublishSqlTest`：实际 XML 的状态/旧版本参数绑定，SKU 查询过滤与有界读取，以及该查询所用 JSON ResultMap/TypeHandler。
- Service 测试通过 Spring 事务代理验证开启/提交/回滚调用顺序；事务管理器和 Mapper 为 Mock。

执行全量回归：

```bash
/Users/a1/tools/apache-maven-3.9.9/bin/mvn clean verify
```

若当前受限环境报 Mockito 无法动态 self-attach，可在测试 JVM 启动时加载本机已安装且与依赖一致的 Agent。本次验证使用：

```bash
/Users/a1/tools/apache-maven-3.9.9/bin/mvn clean verify \
  -DargLine='-javaagent:/Users/a1/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar'
```

该路径为本机路径，不应写入项目通用构建配置。测试未连接 MySQL，不能据此宣称已经验证真实数据库事务回滚、行锁互斥或部署结果。

## 待手工验收

使用测试环境和专用商品，先重启/部署本次构建的应用，避免测试到旧版本。不要用生产商品做测试。

1. 准备启用叶子类目、无品牌草稿商品，生成一个默认 SKU。读取详情，记录状态和版本。
2. 调用上架接口。再次查详情与数据库：`status=1`，版本恰好比调用前加 1，商品资料及 SKU 不应被修改。
3. 重复上架：返回状态错误，数据库版本不再增加。
4. 准备另一件完整多规格草稿商品，确认所有组合齐全，使用其当前版本上架并核对状态。
5. 对草稿商品传过期版本：返回版本冲突，状态、版本和审计字段不变。
6. 覆盖禁用品牌、未绑定品牌、非叶子类目、没有 SKU：请求失败且商品状态不变。
7. 真实并发验证：对同一草稿用同一个版本同时发送两次上架请求，应只有一次成功，最终版本仅增加一次。
8. 另做上架与 SKU 修改的并发验证；Mock 测试不能替代此项。

## 并发与业务边界

上架在一个事务中按 SPU → 类目 → 品牌加锁，SKU 生成/修改与草稿修改沿用 SPU 行锁。
这是本次操作的协调协议，并不表示商品上架后关联资料永远不变。
类目树新增/移动尚需单独审计统一锁协议；本轮没有修改这些既有接口。
品牌或类目在上架完成后禁用时是否自动下架，以及是否允许在已关联商品的叶子类目下新增子类目，仍需独立明确规则。
本次不增加自动下架、库存或上架后的编辑权限。
