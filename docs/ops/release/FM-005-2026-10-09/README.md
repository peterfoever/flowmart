# FM-005 test 发布与验收记录

执行日期：2026-10-09（北京时间）；2026-10-10继续整理归档。

## 1. 发布结论与版本边界

- 业务补丁 [PR #10](https://github.com/peterfoever/flowmart/pull/10) 在必需CI通过后合并。
- 发布业务源码：`73be4873666543d2bf5a8019bf7eeea664b42c2f`，不是原先遗漏修复的PR #9版本。
- 从该合并提交建立干净检出，重新执行 `mvn clean verify`：469测试，0失败、0错误、0跳过。
- 远程和合并提交均包含 `SpuPageServiceTest`、`SpuPageControllerTest`、`SpuPageSqlTest`。
- 实际运行镜像：`flowmart:73be487-jar`；运行中的JAR与合并版本构建产物SHA256一致。
- 本次证据提交还包含test探针环境变量修正和Postman百分号编码修正；不改变业务Java代码或DDL。
- test应用、MySQL、Redis均healthy；readiness为UP；接口文档及OpenAPI返回HTTP200。
- 最终Postman/Newman：39请求、125断言，全部通过。首次失败也保留，不伪装成一次全绿。

这次完成了test发布、功能验收和小数据量执行计划归档。**没有完成万级数据性能专项、实际回滚演练或生产发布。**

## 2. 环境与端口

| 项目 | 本次实际值 |
|---|---|
| 应用 | http://127.0.0.1:8081 |
| MySQL | 127.0.0.1:3308 / flowmart_test，MySQL 8.0.46 |
| Redis | 127.0.0.1:6381 |
| 容器内部连接 | mysql:3306、redis:6379 |
| 数据库会话时区 | +08:00 |
| Docker资源 | aarch64，8 CPU，8321798144字节内存（约7.75GiB） |
| 数据 | 全新独立test卷，32条专属SPU，31条有效 |

3307/6380已由hm-dianping项目占用，未停止或修改它的容器；dev/prod数据未改动。
使用环境变量覆盖test映射端口；映射只绑定127.0.0.1。
部署命令（从仓库根目录运行，确保该镜像仍存在）：

```bash
TEST_MYSQL_PORT=3308 TEST_REDIS_PORT=6381 TEST_APP_PORT=8081 \
IMAGE_TAG=73be487-jar docker compose -f deploy/test/docker-compose.yml up -d
```

此命令是本次版本的复现记录，不代表应将以后发布也固定为73be487。
若不传IMAGE_TAG，原Compose会回退latest，不能用于可追溯发布。

## 3. 构建与镜像来源

[PR CI](https://github.com/peterfoever/flowmart/actions/runs/37907711474)：构建测试通过，PR阶段镜像任务按规则跳过。
[合并后CI](https://github.com/peterfoever/flowmart/actions/runs/37908343779)：构建测试和完整Dockerfile镜像构建均通过。

本机完整多阶段构建长时间停在Maven冷缓存依赖预热，诊断请求Maven Central返回200，但构建尚未完成。
为避免把等待误报为成功，该构建已取消，原始日志保留在 `docker-build.txt`。
随后用干净合并检出经 `clean verify` 生成的JAR，按相同运行阶段打包，配方见 `Dockerfile.runtime`。
该运行阶段没有业务源码覆盖，也没有使用旧target目录。镜像使用独立的 `-jar` 后缀，不冒充原多阶段构建产物。

JAR SHA256（构建产物与容器内 `/app/app.jar` 相同）：

```text
f34e21b5660bcdd29b663fb46a6e716780ff7589b324b387247a6eadca5f078f
```

运行镜像标识：

```text
sha256:40bbf551eacadfe16b508e9040a721ac35a19962b8312713db861c19b948a280
```

上述身份由 `jar-sha256.txt`、`running-jar-sha256.txt`、`image-identity.txt` 和 `container-state.txt` 交叉确认。
Maven详细日志中包含异常分支测试主动制造的堆栈；测试成败以 `test-summary.txt` 的汇总及verify退出码为准。

## 4. 发布中发现并处理的问题

### 4.1 readiness返回404

原配置启用了liveness/readiness指标，但在非Kubernetes容器里没有显式启用探针健康组。
基础 `/actuator/health` 是UP，而Docker使用的 `/actuator/health/readiness` 返回404。
在test Compose中补 `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`，重建应用容器后验证readiness为UP、容器healthy。
这属于test运行配置修正，未更改业务镜像内的JAR。

### 4.2 Newman百分号用例失败

首次运行39请求/125断言，其中百分号用例两条断言失败，total错误地为31。
原集合raw URL有编码，但结构化query.value末尾保留裸百分号；Newman6.2.1发出的请求未正确编码该字符。
用 `curl --get --data-urlencode 'keyword=FM005QA20261009 折扣100%'` 对照，服务端正确返回007这1条。
将集合query.value的百分号编码为 `%25` 后，原断言不变，重跑全部39请求/125断言通过。
URL编码与后端LIKE转义是不同层次；不能通过放宽预期数量掩盖错误。

## 5. 数据库与接口验收

- Flyway首次建库成功应用V1～V7，无新DDL、无已执行迁移脚本改写。
- 夹具导入前四项冲突预检全为0，随后事务插入并显式COMMIT。
- 夹具只写独立flowmart_test；其中失效关联是专门的读取测试数据，不用于写接口/上架验收。
- 断言覆盖分页、排序、条件组合、叶子/父类目、禁用/失效关联、单端时间、左闭右开、特殊字符及非法参数。
- 最终单次串行运行约814ms，HTTP平均约8ms；这是小数据量冒烟结果，**不是并发吞吐、性能SLA或压测结论**。
- 已保留32条夹具以便用户复验，没有执行cleanup，也没有删除数据卷。

## 6. EXPLAIN观察与限制

原始输出：`explain.txt`（传统计划）与 `explain-analyze.txt`（真实执行树）。
均在上述test库、32条SPU上执行，脚本包含五组COUNT/列表及类目递归CTE。

| 场景 | COUNT | 列表主表访问 |
|---|---|---|
| A 无筛选 | idx_deleted_status_created，ref，估计31行，覆盖读取 | ALL，估计32行，Using where; Using filesort |
| B 状态0+10月7日时间范围 | 同一组合索引，range，估计8行 | range，估计8行，Backward index scan，无额外filesort |
| C 数码类目范围 | 使用deleted/status时间索引后过滤，实际匹配23条 | 小表选择ALL并排序；关联类目/品牌按PRIMARY做eq_ref |
| D 名称包含100% | ALL，实际匹配1条 | ALL并排序，转义后只匹配字面量百分号 |
| E OFFSET 5000 | 与无筛选COUNT相同 | 读取有限夹具后返回0条，不能作为深分页性能证据 |

示例ANALYZE根节点完成时间：A列表约0.102ms，B列表约0.0953ms。
这些只是一轮样本，缓存/环境会影响结果，不用于比较哪个方案更快。
关键结论：COUNT与列表不必选同一个索引；B的条件使组合索引能按目标顺序反向读取；
小表ALL不直接说明缺索引，不能仅凭filesort就新增索引。
下一步性能专项需约1万条可追踪数据、多轮重复、匹配量大于offset，再判断优化方向。

## 7. 回退方案与未完成项

这是首次创建Flowmart test环境，没有上一个已验证运行镜像，因此未执行“回滚到上一版”的演练。
如本次版本异常，可先只停止 `flowmart-app-test` 或用Compose `stop app` 止血，保留MySQL/Redis卷；
修复后从明确提交重新构建发布。不得使用 `down -v`、清库或回滚Flyway历史。
未来有上一正常镜像时，记录其标签/ID后仅替换app镜像回滚，再做冒烟；数据库兼容性需另行评估。

FM-005仍保留两项专项：万级数据执行计划/重复测量、实际回滚演练。此次没有新增索引或承诺高并发能力。

## 8. 证据索引

- 版本与CI：`source-commit.txt`、`merged-test-files.txt`、`pr-ci.json`、`merged-ci.json`。
- 合并版验证：`test-summary.txt`、`merged-verify.txt`。
- 构建/运行身份：`Dockerfile.runtime`、`runtime-image-build.txt`、`image-identity.txt`、两份JAR校验文件。
- 发布：`compose-up.txt`、`container-state.txt`、`application.txt`、`readiness.json`、`api-doc-smoke.txt`。
- 数据：`fixture-import.txt`、`database-state.txt`、`docker-resources.txt`。
- 首次Postman：`postman-cli.txt`、`postman-junit.xml`、`postman-first.json.gz`（保留2个断言失败）。
- 最终Postman：`postman-final.txt`、`postman-final.xml`、`postman-final.json.gz`（125断言全通过）。
- 查询计划：`explain.txt`、`explain-analyze.txt`，执行脚本在 `docs/testing/FM-005/02-explain.sql`。

JSON报告用gzip无损压缩以减少仓库体积；可用 `gzip -dc 文件.json.gz` 读取，不需要删除压缩件。
