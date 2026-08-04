# flowmart · 电商供应链履约中台

一个用于**中级 Java 工程师能力训练**的实战项目。业务覆盖商品、库存、订单、履约、采购、结算全链路，
按每日一张需求卡的节奏持续演进。

技术栈：`JDK 21` · `Spring Boot 3.3` · `MyBatis-Plus` · `MySQL 8` · `Redis 7` · `Flyway` · `Docker` · `GitHub Actions`

---

## 🚀 快速开始

```bash
# 0. 前置：JDK 21、Maven 3.9、Docker Desktop
java -version && mvn -v && docker -v

# 1. 起本地依赖（MySQL + Redis）
cd deploy/dev && docker compose up -d && docker compose ps

# 2. 启动应用
#    首次、或改动了 flowmart-common 之后，要先 install 一次让下游模块拿到最新产物
cd ../.. && mvn install -DskipTests
mvn -pl flowmart-bootstrap spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 验证
curl -s localhost:8080/actuator/health      # {"status":"UP"}
open http://localhost:8080/doc.html         # 接口文档
```

## 📁 项目结构

```
flowmart/
├── flowmart-common/          通用基础设施（统一返回、异常体系、分页、审计、链路追踪）
├── flowmart-bootstrap/       启动模块、三套环境配置、Flyway 数据库脚本
├── deploy/
│   ├── dev/                  开发环境 docker-compose
│   ├── test/                 测试环境 docker-compose（应用也跑容器里）
│   └── prod/                 生产环境 docker-compose
├── docs/
│   ├── TRAINING-PLAN.md      ⭐ 12 周训练大纲，先读这个
│   ├── prd/                  需求文档（每张需求卡一个文件）
│   ├── backlog/              需求池
│   ├── standards/            Git 规范、编码规范
│   ├── ops/                  环境与发布手册、发布单
│   ├── adr/                  架构决策记录
│   └── retro/                周复盘
├── .github/workflows/ci.yml  CI 流水线
└── Dockerfile                多阶段构建
```

## 📖 文档导航

| 我想… | 看这个 |
|---|---|
| 了解整个训练怎么安排的 | [docs/TRAINING-PLAN.md](docs/TRAINING-PLAN.md) |
| 开始今天的需求 | [docs/prd/](docs/prd/) 里找当天编号 |
| 知道怎么提交代码、提 PR | [docs/standards/GIT.md](docs/standards/GIT.md) |
| 知道代码该怎么写 | [docs/standards/CODING.md](docs/standards/CODING.md) |
| 发布到测试/生产环境 | [docs/ops/DEPLOY.md](docs/ops/DEPLOY.md) |
| 线上出问题了怎么查 | [docs/ops/DEPLOY.md](docs/ops/DEPLOY.md) 第五节 |

## 🎯 当前进度

| 阶段 | 周次 | 状态 |
|---|---|---|
| 阶段零 · 工程骨架 | - | ✅ 已完成 |
| 阶段一 · 打地基（商品中心） | W1-W2 | 🔵 进行中 |
| 阶段二 · 核心难点（库存/并发/订单/一致性） | W3-W7 | ⚪ 未开始 |
| 阶段三 · 贴近生产（履约/性能/采购/结算/生产化） | W8-W12 | ⚪ 未开始 |

**下一张卡**：[FM-001 商品类目管理](docs/prd/FM-001-商品类目管理.md)
