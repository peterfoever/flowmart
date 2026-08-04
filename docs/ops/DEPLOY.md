# 环境与发布手册

> 「能操作测试环境和生产环境」是中级工程师和初级最直观的分界线。
> 这份文档你要练到不看也能操作，并且**知道每一步在做什么、出错了怎么退**。

## 一、三套环境

| | dev | test | prod |
|---|---|---|---|
| 用途 | 你自己开发调试 | 提测验证，模拟真实 | 生产 |
| 应用运行方式 | IDE 直接跑 | Docker 容器跑 jar | Docker 容器跑 jar |
| MySQL | localhost:3306 | localhost:3307 | 环境变量注入 |
| Redis | localhost:6379 | localhost:6380 | 环境变量注入 |
| 应用端口 | 8080 | 8081 | 8080 |
| 配置来源 | 配置文件明文 | 环境变量（有默认值） | 环境变量（**无默认值，缺一个就起不来**） |
| 接口文档 | 开启 | 开启 | **关闭** |
| 日志级别 | DEBUG | DEBUG | INFO（root 为 WARN） |
| flyway clean | 允许 | 禁止 | 禁止 |
| 数据 | 随便删 | 不能随便删 | 碰之前先想三遍 |

> **注意**：prod 环境在这个训练里跑在你本机（`deploy/prod`），但**所有操作规范完全按真实生产来**。
> 你要养成的是习惯，不是敲命令。

## 二、dev 环境：日常开发

```bash
# 1. 起依赖组件（MySQL + Redis）
cd deploy/dev && docker compose up -d

# 2. 等健康检查通过
docker compose ps            # STATUS 都是 healthy 才继续

# 3. 启动应用（IDE 里跑 FlowmartApplication，或命令行）
cd ../.. && mvn -pl flowmart-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 验证
curl -s localhost:8080/actuator/health | jq
open http://localhost:8080/doc.html
```

**重来一次（表改乱了）**：
```bash
cd deploy/dev && docker compose down -v && docker compose up -d
```

## 三、test 环境：提测发布

这是你每天下午要走的流程。**每次发布都必须填发布单**（`docs/ops/release/` 下按日期建文件）。

```bash
# ---------- 发布前 ----------
# 1. 确认要发的代码已合入 develop 且 CI 绿灯
git checkout develop && git pull

# 2. 本地跑一遍完整测试，不要指望 CI 兜底
mvn clean verify

# ---------- 发布 ----------
# 3. 构建镜像，用 commit sha 作为 tag（绝不用 latest —— 你会分不清线上跑的是哪个版本）
export IMAGE_TAG=$(git rev-parse --short HEAD)
docker build -t flowmart:${IMAGE_TAG} .

# 4. 启动/更新 test 环境
cd deploy/test && IMAGE_TAG=${IMAGE_TAG} docker compose up -d

# ---------- 发布后验证（这一步不能省） ----------
# 5. 看启动日志，确认 Flyway 迁移执行成功、没有 ERROR
docker compose logs -f app | head -100

# 6. 健康检查
curl -s localhost:8081/actuator/health/readiness

# 7. 冒烟测试：至少调通本次需求涉及的核心接口
curl -s localhost:8081/api/product/categories/tree | jq

# 8. 回填发布单：版本号、发布内容、验证结果、耗时
```

**回滚**（发现问题时，先回滚再排查，不要在线上调试）：
```bash
cd deploy/test
IMAGE_TAG=<上一个正常版本的sha> docker compose up -d app
```

> ⚠️ **数据库变更无法靠回滚镜像撤销。** 这是发布里最危险的部分。
> 所以本项目要求：**每个 Flyway 脚本在 PR 里都必须写明回滚 SQL**。
> 加字段可以直接回滚，删字段/改类型必须走「先兼容、再迁移、后清理」三步走。

## 四、prod 环境：生产发布

生产发布比 test 多四道关卡，一道都不能跳：

```
1. 发布前 —— 发布评审
   □ 变更内容是什么，影响哪些接口/表
   □ 数据库变更的回滚方案是什么
   □ 是否需要停机？影响多少用户？
   □ 回滚触发条件是什么（错误率 > X%？响应时间 > Yms？）
   □ 发布窗口：避开业务高峰

2. 发布中 —— 灰度
   □ 先发一个实例，观察 10 分钟
   □ 看错误日志、看 QPS、看 RT、看 DB 连接数
   □ 无异常再发剩余实例

3. 发布后 —— 观察期
   □ 持续观察 30 分钟
   □ 核心接口冒烟测试
   □ 确认无异常后在群里同步「发布完成」

4. 异常时 —— 回滚
   □ 先回滚，后排查。不要在生产环境 debug
   □ 回滚后同步事故信息，24h 内出复盘文档
```

```bash
# 生产发布（本训练用脚本模拟，实际公司里通常是 Jenkins/ArgoCD 点一下）
cd deploy/prod
cp .env.example .env          # 首次：填入真实的 DB/Redis 密码
vim .env
IMAGE_TAG=v1.1.0 docker compose up -d
```

## 五、线上排障速查

出问题时按这个顺序查，**不要一上来就看代码**：

```bash
# 1. 应用还活着吗
curl -s localhost:8081/actuator/health | jq

# 2. 最近的错误日志（生产上用 traceId 精确定位）
docker compose logs --tail=200 app | grep ERROR
grep "3f2a1b9c8d7e6f50" logs/flowmart.log     # 按 traceId 捞完整链路

# 3. 是不是卡在数据库
docker exec -it flowmart-mysql-test mysql -uroot -proot123 -e "SHOW FULL PROCESSLIST;"
docker exec -it flowmart-mysql-test mysql -uroot -proot123 -e \
  "SELECT * FROM information_schema.INNODB_TRX\G"     # 看有没有长事务

# 4. 是不是线程/内存问题
docker exec -it flowmart-app-test jcmd 1 Thread.print > /tmp/thread.txt
docker exec -it flowmart-app-test jcmd 1 GC.heap_info

# 5. 慢查询
docker exec -it flowmart-mysql-test cat /var/lib/mysql/slow.log | tail -50
```

**排障心法**：先止血（回滚/降级/限流），再定位，最后修复。
顺序反了就是在用用户的体验给自己的好奇心买单。

## 六、发布单模板

新建 `docs/ops/release/2026-08-07-test.md`：

```markdown
# 发布单 · test 环境 · 2026-08-07

| 项 | 内容 |
|---|---|
| 环境 | test |
| 版本 | a3f9c21 |
| 发布人 | @你 |
| 发布时间 | 17:05 - 17:12 |

## 发布内容
- FM-001 商品类目管理

## 数据库变更
- V2__product_category.sql：新增 `product_category` 表
- 回滚方案：`DROP TABLE product_category;`（新表，无数据依赖，可直接删）

## 验证结果
- [x] 应用启动正常，Flyway 迁移成功
- [x] /actuator/health 返回 UP
- [x] 类目树接口返回正常
- [x] 新增/编辑/删除接口冒烟通过

## 问题与处理
（无 / 或记录发布中遇到的问题）
```
