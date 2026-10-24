# FM-007 骨架验证与后续练习

## 本次范围

2026-10-22 验证当前功能分支工作区：全量 `clean verify` 成功，
652 个测试通过（product 640、bootstrap 12），0 失败、0 错误、0 跳过。
相比此前 596 个测试新增 56 个用例。这不是完整导入功能的验收，也不是远程 CI 结果。

- 调整 V8/V10 的规范化标识列为 `utf8mb4_bin`。
- V9 请求幂等键采用 MySQL 8 的 `utf8mb4_0900_bin`，区分大小写及尾部空格。
- 保留 V11 每批次每行一条错误的唯一约束，命名为 `uk_import_row_error_batch_row`。
- 补商品标识规范化、请求指纹、实体映射和真实 MySQL 测试。

用户已确认 V8～V11 未在 dev/test 执行，所以此次直接修改这些版本；
一旦在共享环境执行，之后必须新增迁移，不能继续改旧版本。V1～V7 未修改。
本次使用不挂载宿主机目录的临时 MySQL 8.0.46，没有连接 dev/test 数据库。

## 工具契约

### ImportItemKeyNormalizer

统一通过 `normalize(rawKey)` 分组、查重和落库：

1. Hutool trim 清理首尾空白（包含全角空格）。
2. 只接受 1～64 位 ASCII 字母、数字、下划线、连字符。
3. 使用 `Locale.ROOT` 转大写；不压缩内部空白、不截断非法标识。

保留原始键用于错误展示；规范化键作为身份。非法键的行进入行错误，不创建虚假的空商品组。
如果非法原文超过行错误表字段长度，错误展示值须安全截断，不能把截断值当作分组键。

### ImportRequestFingerprint

`calculate(fileHash, templateVersion, optionsJson)` 输出小写 SHA-256：

- fileHash 必须为 64 位十六进制文本，先统一为小写。
- templateVersion 由服务端给定，按原文比较，1～64 字符且不全为空白。
- optionsJson 必须是对象；无选项传 `{}`。对象键递归排序、数字去无意义尾零，
  数组顺序和字符串内容保留。重复 JSON 键、尾随 JSON、超长/过深输入拒绝。
- 哈希输入是含算法版本的结构化 JSON，不是几个字符串直接相加。
- 固定测试向量锁住 v1；后续算法变化要设计版本兼容，不能让已有批次重试突然冲突。

调用者先把默认业务选项展开，再计算指纹；工具不知道“省略字段”与“显式默认值”是否等价。
请求幂等键不参与指纹。相同 owner + 请求键但指纹不同，未来 Service 应返回冲突，
而不是覆盖旧批次。相同文件哈希不是拒绝条件，只做提醒。

## 实体及事务约定

四张表不继承 BaseEntity，不加 deleted/version。防重凭证不是普通商品的逻辑删除数据。

- `IdType.INPUT`：Writer 显式生成雪花 ID，不能依赖 MyBatis 自动生成。
- `ownerId` 是归属主体，`operatorId` 是本次操作人，审计字段使用操作人。
- `warnings` 当前是原始 JSON 数组字符串，创建时显式写 `"[]"`，
  不能附加 JacksonTypeHandler 把数组再次编码成 JSON 字符串。
- 来源映射允许事务内占位，但必须与商品、SKU、成功结果、回填一起提交。
  本次回滚测试只证明数据库占位可回滚，不代表尚未实现的 Spring 组事务已经验收。
- 一条失败行只记一条代表错误。多个字段错误选择优先错误或合并描述，不能重复写同一行。
- 系统异常/提交结果不确定时中止，不把它伪装成“该组已失败，继续下一组”。
- INTERRUPTED 不应仅凭“未处理组数 > 0”判定，收尾失败也可能需要核对结果。

## 怎么复现

普通 `mvn clean verify` 会运行纯单元/映射测试；
未提供 `import.mysql.url` 时，`ImportSchemaMysqlTest` 明确跳过。
目前 CI 未配置该属性，因此 CI 绿灯不能替代下面的真实数据库验证。

先创建仅用于此次测试的容器（固定名字冲突时先核对旧容器，不要盲删）：

```bash
docker run -d --name flowmart-fm007-check \
  --label flowmart.task=FM-007-verification \
  -e MYSQL_ROOT_PASSWORD=fm007-local-only \
  -e MYSQL_DATABASE=fm007_check \
  -p 127.0.0.1::3306 mysql:8.0
docker logs --tail 10 flowmart-fm007-check
docker port flowmart-fm007-check 3306
```

等待日志出现 ready for connections；将下面的 PORT 替换为上一步显示的端口：

```bash
cd /Users/a1/my-claude-project/flowmart
/Users/a1/tools/apache-maven-3.9.9/bin/mvn clean verify \
  '-Dimport.mysql.url=jdbc:mysql://127.0.0.1:PORT/fm007_check?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai' \
  -Dimport.mysql.user=root \
  -Dimport.mysql.password=fm007-local-only
```

以上密码仅是一次性本地测试密码，不可用作 dev/test/prod 密码。
如果受限运行环境阻止 Mockito 自动挂载 agent，可额外传：

```text
-DargLine=-javaagent:/Users/a1/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar
```

测试只接受本机显式端口、库名为 `fm007_check` 的连接，不读取应用数据源配置。
Flyway 执行全部迁移并再次验证无重复迁移；每个测试数据回滚，不清空共享数据库。
确认测试通过后清理刚才创建的临时容器及其匿名卷：

```bash
docker inspect --format '{{.Name}} {{index .Config.Labels "flowmart.task"}}' flowmart-fm007-check
docker rm -f -v flowmart-fm007-check
```

## 下一步练习

先完成“解析与分组”，不要一口气把上传、解析、建商品全部塞进一个 Service：

1. 定义解析行 DTO，保存 Excel 真实行号、原始 importItemKey 和规范化值。
2. 事务外解析和分组，使用上述工具；非法标识行与合法商品组分开。
3. 组内核对名称、类目、品牌、主图、规格定义一致；无规格商品只能有一行。
4. 先写测试：同键大小写合并、非连续行同组、非法键不能归组、
   同组字段不一致、无规格多行拒绝。

这一步通过评审后，再实现独立 Bean 的 GroupWriter 和 FailureWriter。
后续必须增加 Spring 事务代理与真实商品/SKU回滚测试；当前数据库约束测试不能替代它们。
