# Git 协作规范

> 本项目严格按真实团队的方式协作。哪怕现在只有你一个人，也必须走完整流程 ——
> 你练的不是 git 命令，是**多人协作时的肌肉记忆**。

## 一、分支模型

```
main       生产分支。只接受来自 release/* 和 hotfix/* 的合并。每次合并 = 一次生产发布，必须打 tag
develop    集成分支。日常开发的合并目标，对应 test 环境
feature/*  功能分支。从 develop 切出，做完合回 develop
release/*  发布分支。从 develop 切出做发布前验证，完成后同时合入 main 和 develop
hotfix/*   紧急修复。从 main 切出，修完同时合入 main 和 develop
```

**铁律：任何人不得直接 push 到 `main` 和 `develop`，一律走 PR。**
GitHub 上已配置分支保护，你会发现自己想直推也推不上去 —— 这是故意的。

## 二、分支命名

```
feature/FM-001-product-category      功能：feature/{需求编号}-{英文简述}
bugfix/FM-010-category-tree-npe      缺陷：bugfix/{需求编号}-{英文简述}
hotfix/20260803-order-timeout        紧急：hotfix/{日期}-{英文简述}
release/v1.1.0                       发布：release/v{版本号}
```

## 三、提交信息规范（Conventional Commits）

```
<type>(<scope>): <subject>

<body>

<footer>
```

**type 取值**：

| type | 用于 | 例 |
|---|---|---|
| `feat` | 新功能 | `feat(product): 支持类目移动与环路检测` |
| `fix` | 修复缺陷 | `fix(product): 修复删除类目时未校验子类目` |
| `refactor` | 重构，不改变外部行为 | `refactor(common): 抽取分页转换工具` |
| `perf` | 性能优化 | `perf(product): 类目树查询改为单次全量加载` |
| `test` | 补测试 | `test(product): 补充环路检测边界用例` |
| `docs` | 文档 | `docs(prd): 补充 FM-001 需求澄清结论` |
| `chore` | 构建/依赖/配置 | `chore: 升级 mybatis-plus 到 3.5.9` |
| `ci` | CI 配置 | `ci: 增加单测覆盖率门槛` |

**规则**：
- `subject` 用中文，不超过 50 字，说清「做了什么」，不要写「修改代码」这种废话
- 涉及需求的提交，`footer` 写 `Refs: FM-001`
- 破坏性变更（接口出参变了、字段删了）必须在 `footer` 写 `BREAKING CHANGE: ...`

**反面例子**（这些提交信息在 Review 时会被打回）：
```
update            ← 更新了啥？
修复bug           ← 哪个 bug？
最终版            ← 三天后你自己也不知道这是啥
提交一下          ← ……
```

## 四、提交粒度

**一次提交只做一件事。** 判断标准：这次提交的改动，能不能用一句话说清楚且不带「以及」「顺便」。

```bash
# ❌ 一把梭
git add . && git commit -m "完成类目管理"

# ✅ 小步提交，每步都是可回滚的完整单元
git commit -m "feat(product): 新增类目表结构与 Flyway 脚本 V2"
git commit -m "feat(product): 实现类目新增与层级自动计算"
git commit -m "feat(product): 实现类目树查询"
git commit -m "test(product): 补充环路检测单元测试"
```

小步提交的实际价值：线上出问题时你能用 `git bisect` 快速定位到是哪次改动引入的。
一把梭提交会让你在事故现场束手无策。

## 五、完整工作流（每张需求卡都走一遍）

```bash
# 1. 同步最新 develop
git checkout develop && git pull origin develop

# 2. 切功能分支
git checkout -b feature/FM-001-product-category

# 3. 开发，小步提交
git add flowmart-product/src/main/java/com/flowmart/product/entity/
git commit -m "feat(product): 新增类目实体与 Mapper"

# 4. 推送
git push -u origin feature/FM-001-product-category

# 5. 提 PR（填 PR 模板）
gh pr create --base develop --fill

# 6. Review 有意见 → 改 → 追加提交 → 再推
# 7. 通过后合入。合入方式用 Squash Merge，保持 develop 历史干净
# 8. 清理本地分支
git checkout develop && git pull && git branch -d feature/FM-001-product-category
```

## 六、遇到冲突怎么办

```bash
# 推荐：rebase 到最新 develop，保持线性历史
git checkout feature/FM-001-product-category
git fetch origin
git rebase origin/develop
# 解决冲突后
git add <冲突文件>
git rebase --continue
git push --force-with-lease    # 注意是 --force-with-lease，不是 --force
```

> `--force-with-lease` 会在远端有别人的新提交时拒绝推送，`--force` 会直接覆盖掉同事的工作。
> 这是真实事故的高发点，务必用前者。

## 七、版本号（语义化版本）

```
v{主版本}.{次版本}.{修订号}      例：v1.2.3
主版本  不兼容的变更（接口契约破坏）
次版本  向下兼容的新功能
修订号  向下兼容的缺陷修复
```

每次合入 `main` 都要打 tag：
```bash
git tag -a v1.1.0 -m "release: 商品中心基础功能"
git push origin v1.1.0
```
