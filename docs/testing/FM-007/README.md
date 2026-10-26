# FM-007 导入实现与验收

## 2026-10-26 完整实现与本地验收

当前实现不再仅限骨架。需求和接口契约见 [FM-007需求卡](../../prd/FM-007-商品Excel批量导入.md)。
此前各节保留为历史记录，不代表目前尚未实现Reader/Writer。

已接通：模板下载 → 私有原文件存储 → 流式文件读取 → 行校验/分组 → 批量预检 →
逐SPU独立事务 → 错误报告独立事务 → 持久化统计收尾 → 批次/分组/行错误查询。
包含请求幂等、跨文件防重、错误xlsx下载及无进展批次超时中断。

Reader采用Apache POI 5.5.1的事件读取接口配合安全StAX，保留原始文本/数字/公式类型；
没有使用将数字ID格式化为字符串的便捷映射，也不依赖EasyExcel读取公式缓存。
ZIP与XML分别限制资源使用，XML外部实体禁止。模板及报告由POI生成。

### 自动化验证

本次构建命令、源码基线及jar校验值见 [验证记录](verification-2026-10-26.md)。

2026-10-26 本地全量 `clean verify`：product 853个 + bootstrap 27个 = **880个通过，0失败、0错误、0跳过**。
bootstrap含12个真实数据库迁移/约束用例及15个Spring端到端用例（并发场景重复3轮）。
使用专用临时MySQL 8.0容器及fm007_check库，不连接dev/test库；HTTP通过MockMvc走真实Controller和Service。
这不是远程CI、人工Postman或test环境发布的验收记录。

覆盖重点：

- 正常带品牌导入、默认SKU、正确审计、来源回填、草稿状态。
- 同请求键重放、不同指纹冲突、不同请求键重复商品、不同owner隔离。
- 同请求键并发、同来源并发；只产生一份商品，不将主键等其他唯一冲突冒充来源重复。
- 同组坏行整组失败，其他组继续；失败修正后新请求可导入。
- 201个SKU第二批由MySQL触发器注入失败，确认第一批、SPU、来源占位全部回滚。
- 错误报告写入失败，确认失败组结果回滚并中止后续组。
- 预检后引用变化的锁内复核、无品牌预检、无归属行统计、跨owner查询拒绝。
- 收尾失败/系统故障不伪造业务失败；超时中断再次检查心跳。
- ZIP膨胀、恶意XML、超限、非文本ID、公式、真实行号、错误报告公式注入防护。

复现命令沿用下方隔离MySQL说明。数据库必须为本机显式端口的fm007_check。
**ProductImportMysqlTest会清空该隔离库中的商品及导入测试表**，不能用于任何共享业务库。
新增V12仅添加超时扫描索引，V1～V11未修改。
CI配置现在包含一次性MySQL服务，并传入隔离库属性，避免数据库测试在CI中静默跳过。

### Postman验收

导入 [FM-007.postman_collection.json](FM-007.postman_collection.json)。设置baseUrl（dev默认8080，test按实际端口）。
先执行01下载模板并保存；手工填完后，在02和03的Body → form-data → file选择**同一个文件**。
不要手工填写HTTP Content-Type整体请求头，multipart boundary由Postman生成；文件part使用xlsx MIME。
新的一次上传修改集合变量idempotencyKey；超时重试保留该键且不重新保存/改动原文件。

最低验收数据（类目ID先替换成你的启用叶子类目，Excel列必须为文本）：

| importItemKey | SPU名称 | 类目ID | 品牌ID | 主图URL | 规格定义 | 规格组合 | 销售价 | SKU图片URL |
|---|---|---|---|---|---|---|---|---|
| FM007-OK-001 | 导入测试商品 | 替换成真实ID | 留空 | https://example.com/main.png | 留空 | 留空 | 12.30 | https://example.com/sku.png |

第一轮预期SUCCEEDED、successSpuCount=1、successRowCount=1，数据库新增草稿SPU和默认SKU。
03重放应replayed=true、batchId不变，商品数量不增加。
改idempotencyKey但仍传原文件：FAILED，组结果IMPORT_ITEM_DUPLICATED，warnings含FILE_ALREADY_IMPORTED。

第二轮用新文件、新idempotencyKey，使用从未成功导入的标识：

1. 增加一行合法无规格商品A。
2. 增加两行同标识B，F列均填 `[{"name":"颜色","values":["红","蓝"]}]`。
3. B第一行G为 `[{"name":"颜色","value":"红"}]`，价格正常；第二行G为蓝，价格填“十二元”。
4. 预期PARTIAL、totalSpuCount=2、successSpuCount=1、failedSpuCount=1、successRowCount=1、failedRowCount=2。
5. 06查询错误：B两行均有记录，具体坏行是SKU_PRICE_INVALID，其余行为GROUP_FAILED；07可下载对应报告。
6. 只上传修正后的B，用新请求键，预期成功；再次上传已成功的A仍应拒绝重复。

返回INTERRUPTED时不要当作普通失败重试：先按batchId核对已落库组结果。
服务暂未集成登录身份，HTTP入口owner/actor固定0，仅用于受控训练环境。

### 本次交付边界

- 已实现并本地验证；没有代替用户commit、push、建PR、合并或发布test/prod。
- test/prod Compose已声明导入文件持久卷，但本次没有启动这些环境；没有执行生产迁移。
- 正式交付仍需人工验收、PR通过远程CI、合并版本构建及test发布记录。
- 原文件不自动删除；目录容量监控、保留/清理策略、鉴权接入属于发布前运维检查项。

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
2026-10-26 起CI已配置隔离MySQL及该属性；本地不传该属性仍会跳过数据库用例。

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

### 2026-10-24 原始单元格与行校验修复

- 补充 `dto.imports.RawImportRow`：保存真实行号、只读单元格映射，缺列按空单元格处理。
  修正 `RawCell.isBlank()`：公式、错误值、数字单元格不能被当成空行跳过。
- `ImportRowValidator` 只接收原始行，返回 `ImportParseResult`，不访问数据库。
  null 行对象、重复行号、行号小于2、模板外列属于调用契约错误，直接拒绝；
  用户填写错误则每行记录一个代表错误，继续检查后续行。
- 标识、名称、URL、规格 JSON 必须来自文本单元格；ID 必须为文本形式的正 Long，
  不接受数字单元格，防止雪花 ID 已被 Excel 舍入后继续导入。
  价格允许文本或数字，范围为 0～99999999.99，最多两位小数，不做舍入修复。
- 公式和错误单元格均拒绝。标识列非文本时，错误记录的 rawKey/normalizedKey 为 null，
  不把公式缓存结果或数字格式化内容当作可信商品标识；这类行不归入商品组。
- 规格空白或 `[]` 表示无规格；JSON `null`、非数组、重复键、尾随内容、
  非字符串名称/值、未知字段、超长或过深 JSON 均拒绝，禁止隐式类型转换。
  定义边界、规格重复及组合匹配仍交给已有分组器校验。
- 仅捕获明确的字段校验错误，系统异常向上抛出；有效标识下的解析失败行会使同组整体失败。
- 新增115个测试（`RawCellTest` 9个、`ImportRowValidatorTest` 106个），
  包含 Validator → Grouper 的内存联动测试。定向184个测试全部通过。
  全量 `mvn clean verify` 成功：product 832个通过，0失败、0错误；
  未配置隔离 MySQL，数据库测试报告9个跳过条目，不能视作数据库验收。
- 下一步实现 Excel Reader：文件/表头检查、原始单元格类型提取、真实行号保留，
  再调用 Validator 和 Grouper。本次尚未实现文件读取、上传接口和商品写库。

### 2026-10-24 解析错误与分组器整合

- 保留 `group(List<ImportRow>)`，委托新入口
  `group(List<ImportRow>, List<RowError>)`。旧有39个分组测试继续通过。
- 同一规范化标识的成功行和错误行进入同一个内部容器。存在解析错误时，
  整组返回 `PARSE_ROW_FAILED`，不再进入合法商品组；只有错误行的组也不会丢失。
- `InvalidGroup.rows` 保留成功解析的连带失败行，
  `parseErrors` 保留具体错误行；每行只允许一个代表错误。
  行数是两类行数之和，起止行号和原始标识取自两类行，输出按真实行号排序。
- 共享行号集合检查两类行的重复/重叠。错误行必须包含错误码、说明，
  其规范化标识必须与原始标识重新计算的结果一致；违约抛 `PARAM_INVALID`，
  不能默默遗漏错误行后继续导入。
- `UngroupedRow` 增加 field、errorCode；这类行只计失败行，不计失败商品组。
- 增加 `ImportParseResult` / `FileLevelError`：
  文件级拒绝不能携带任何部分解析结果，DTO 构造时检查此内部契约；
  编排层仍必须先检查 `hasFileLevelError()`，有错误直接结束，不能调用分组器。
- 新增38个测试（分组解析错误30个、解析结果DTO 8个）。
  `mvn clean verify` 成功，product 共717个测试通过、0失败、0错误。
  本次没有连接数据库，MySQL测试类按配置报告9个跳过条目，未改迁移和Mapper。
- 下一步才接 Excel Reader / Validator；本次没有实现文件读取、字段转换或商品写库。

### 2026-10-24 分组逻辑修复记录

- `ImportRowGrouper` 已修复编译和异常类型问题，内部使用枚举，失败结果保存枚举名称与说明。
- 定义使用 `List<SpecDTO>`，组合使用 `List<SkuSpecValueDTO>`；非法定义不能静默丢弃或去重。
- 复用现有生成器校验定义边界、生成合法组合哈希；校验单行组合匹配与组内组合唯一。
- 保留首次出现的组顺序、非连续原始行号；非法标识单独分流，组失败不影响其他合法组。
- 输入列表的 null 规格项保留到组校验阶段报告；null 行对象、重复行号或小于2的行号属于解析器契约错误。
- 新增 `ImportRowGrouperTest`，39 个用例全部通过。
  本次 `mvn clean verify` 成功，product 共679个测试通过，0失败、0错误；
  未配置隔离数据库，MySQL测试类由JUnit报告9个跳过条目（参数化测试未展开），不是数据库验收。
- 当前只验证内存行 DTO 的分组，不代表 Excel 读取、表头校验、价格校验或数据库写入已实现。

先完成“解析与分组”，不要一口气把上传、解析、建商品全部塞进一个 Service：

1. 定义解析行 DTO，保存 Excel 真实行号、原始 importItemKey 和规范化值。
2. 事务外解析和分组，使用上述工具；非法标识行与合法商品组分开。
3. 组内核对名称、类目、品牌、主图、规格定义一致；无规格商品只能有一行。
4. 先写测试：同键大小写合并、非连续行同组、非法键不能归组、
   同组字段不一致、无规格多行拒绝。

这一步通过评审后，再实现独立 Bean 的 GroupWriter 和 FailureWriter。
后续必须增加 Spring 事务代理与真实商品/SKU回滚测试；当前数据库约束测试不能替代它们。
