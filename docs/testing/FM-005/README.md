# FM-005：Postman 验收与 EXPLAIN 实操

本目录是手工验收材料，不属于 Flyway migration。没有自动导入数据库或调用接口。
夹具包含32条SPU（31条有效、1条逻辑删除）、7个类目、3个品牌、4条绑定。
固定标记为 FM005QA20261009。只验证列表读取，不拿夹具测试创建、上架或SKU生成流程。

## 1. 先确认连接，避免“SQL导入了但接口查不到”

默认面向你自己的隔离 test 环境：

| 项目 | 仓库默认值 |
|---|---|
| IDEA 数据库连接 | 127.0.0.1:3307 / flowmart_test |
| Postman baseUrl | http://127.0.0.1:8081 |
| 本地 dev 对照 | 3306 / flowmart_dev，应用通常8080 |

这些是配置默认值，不代表当前环境已经启动或部署了最新代码。
先确保目标应用包含本次FM-005修复，数据库已执行项目Flyway迁移。
如果只有dev在运行，不要把数据导入test后却请求8080。可在自己的专用测试库调整配置；
不要在共享库/生产库造失效关联夹具，也不要为了测试删除任何数据卷。

IDEA Database 中选择正确数据源，打开 Query Console（查询控制台）。
执行 `SELECT DATABASE(),VERSION();` 确认目标后，分段运行本目录文件。

## 2. 导入数据：01-seed.sql

1. 运行USE与预检SELECT，四项冲突数必须全为0。若不为0，停止，先确认是不是上次夹具。
2. 在同一个控制台运行 START TRANSACTION 到插入后的校验SELECT。
3. 应得到 inserted_rows=32、visible_rows=31；有效状态数：草稿10、上架11、下架10。
4. 确认后在同一控制台单独执行 `COMMIT;`。脚本故意不自动提交。
5. 任一语句报错，先 `ROLLBACK;`，不要在失败事务后继续提交或重复整段插入。

未提交前，Postman使用的其他数据库连接通常看不到新数据。图片是占位URL，不要求能打开。
脚本不覆盖旧数据；重复执行会触发唯一约束，不使用REPLACE或INSERT IGNORE。

## 3. Postman导入现成验收集合

导入本目录 `FM-005.postman_collection.json`，包含39个GET请求，每个都有断言。
将集合变量 baseUrl 改成目标应用地址；检查环境变量中没有同名旧值覆盖它。
可以逐条Send，也可以顺序运行集合。Body保持none；筛选参数在Params中。
断言检查HTTP状态、业务码、总数、记录数量，部分还校验精确排序和失效关联文案。

除特殊字符测试外，每个请求带 `keyword=FM005QA20261009`，
所以预期数量不受旧商品影响。若手工改掉此标记，下表数量就不适用了。
表中“追加参数”叠加到该keyword上；keyword特例直接替换原值。

| 场景 | 追加参数 | 预期 |
|---|---|---|
| 默认分页 | 无 | total=31，records=20 |
| 第1页 | pageNum=1,pageSize=5 | 004、003、032、030、029 |
| 第2页 | pageNum=2,pageSize=5 | 028、027、026、025、024 |
| 最后一页 | pageNum=7,pageSize=5 | total=31，仅001 |
| 超页 | pageNum=8,pageSize=5 | total=31，records=[] |
| 数码父类目 | categoryId=9100051000 | total=23，含手机和电脑 |
| 手机叶子类目 | categoryId=9100051001 | total=13 |
| 食品父类目 | categoryId=9100051100 | total=6，排除已删除子类目 |
| 禁用类目 | categoryId=9100051102 | total=2，允许查询 |
| 正常品牌 | brandId=9100052001 | total=21 |
| 无品牌 | noBrand=true | total=7 |
| 不限制品牌 | noBrand=false | total=31 |
| 禁用品牌 | brandId=9100052002 | total=1，仅004 |
| 状态 | status=0 / 1 / 2 | total分别10 / 11 / 10 |
| AND组合 | categoryId=9100051000,brandId=9100052001,status=0 | total=7 |
| 精确编码 | spuCode=FM005QA20261009-002 | total=1 |
| 逻辑删除 | spuCode=FM005QA20261009-031 | total=0 |
| 百分号字面量 | keyword=FM005QA20261009 折扣100% | 仅007，不包含008 |
| 下划线字面量 | keyword=FM005QA20261009 型号A_B | 仅009，不包含010 |
| 感叹号字面量 | keyword=FM005QA20261009 促销! | 仅011 |
| 10月7日全天 | createdFrom=2026-10-07 00:00:00,createdTo=2026-10-08 00:00:00 | total=29 |
| 仅开始时间 | createdFrom=2026-10-08 00:00:00 | 仅004 |
| 仅结束时间 | createdTo=2026-10-07 00:00:00 | 仅001 |
| 品牌失效 | spuCode=FM005QA20261009-005 或006 | 商品仍显示，brandText=品牌已失效 |
| 类目失效 | spuCode=FM005QA20261009-029 或030 | 商品仍显示，categoryText=类目已失效 |

表中004等表示完整编码FM005QA20261009-004的后缀。
第31条虽然ID更大但已删除；相同创建时间的中间一组按ID倒序。
001/002/003/004分别处在查询日开始前1毫秒、起点、终点前1毫秒、终点，用来检验左闭右开。

以下应返回 code=10001、success=false（项目当前约定HTTP仍为200）：

- brandId=9100052099（不存在）或9100052003（已删除）。
- categoryId=9100051199（不存在）或9100051103（已删除）。
- brandId=9100052001 同时 noBrand=true。
- pageNum显式空字符串、abc；pageSize=201。
- createdFrom=2026-02-30 00:00:00；开始时间等于结束时间。

URL传输中的字面量百分号必须编码成%25，不要自己写SQL的转义字符!。
本集合的百分号用例已将query.value中的百分号保存为%25，以兼容Newman6.2.1；不要再次编码成%2525。
手工使用Postman时检查实际发出的URL，curl可用--data-urlencode传原始值。
无匹配时data.records应为[]，不是null。

## 4. EXPLAIN在哪里做、怎样做

EXPLAIN在IDEA的数据库查询控制台执行，不放进Postman。
打开 `02-explain.sql`，每次选中一条完整SQL执行，保存结果表或复制为文本。

1. 先看 `SHOW INDEX FROM product_spu;`，确认目标库实际索引，而非只看代码里的DDL。
2. 先跑A组默认分页的COUNT和列表，然后跑B组状态+时间；COUNT/列表分别看计划。
3. 再跑C类目范围、D名称搜索、E深页；类目CTE的计划也单独提供。
4. 普通EXPLAIN给估算计划。MySQL8.0.18+可把某条SQL前的EXPLAIN改成EXPLAIN ANALYZE，
   得到真实执行信息；它会实际执行该SELECT，只在受控测试环境做。
   参考：[MySQL EXPLAIN](https://dev.mysql.com/doc/refman/8.0/en/explain.html)。

接口日志也可以作为SQL来源：开发环境通常显示Preparing（?占位符）与Parameters。
分析时把参数按顺序替换成SQL字面量；字符串/时间用单引号，数字不用。
不能把日志的?、MyBatis的#{...}或Postman的{{变量}}直接粘进数据库执行。
脚本里的SQL已按当前Mapper展开，并保留列表真实字段，避免SELECT *改变计划。

## 5. 先读懂六列

| 列 | 观察点 |
|---|---|
| table | 本行分析的表/别名：spu、c、b |
| type | ALL全表扫描、index全索引扫描、range范围、ref等值匹配、eq_ref唯一键关联；结合数据量判断 |
| possible_keys / key | 可能使用的索引 / 真正选中的索引；别把possible_keys当实际命中 |
| rows | 估计每次访问需检查多少行，不是LIMIT条数，也不是精确实测值 |
| filtered | 估计通过本表条件的比例；100不是“性能满分” |
| Extra | Using where继续过滤；Using index覆盖读取；Using filesort额外排序 |

参考：[EXPLAIN列含义](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html)。
Using filesort不等于一定写磁盘或一定慢，可能在内存完成；
参考：[排序优化](https://dev.mysql.com/doc/refman/8.0/en/order-by-optimization.html)。

ANALYZE中的 `actual time=a..b rows=r loops=n`：
a/b是该节点返回首行/完成输出的耗时（毫秒）；重复循环时是每次循环的平均值。
cost是优化器估算代价，不是毫秒。节点时间存在包含关系，不要将整棵树逐行相加当总耗时。
Postman耗时包含HTTP、连接、多条SQL和序列化，不等于一条SQL的actual time。

## 6. 对这个项目应提出的判断

- 已有索引 `(deleted,status,created_at,id)`，状态等值+时间区间值得重点观察；
  没指定status时不能直接断言同一索引也能满足创建时间排序，以计划为准。
- COUNT没有展示JOIN；列表按主键关联类目和品牌。两个SQL不一定使用相同索引。
- 名称是前置通配符的包含搜索，不要以为给name加普通B-tree索引就能高效范围查找。
  参考：[范围优化与LIKE](https://dev.mysql.com/doc/refman/8.0/en/range-optimization.html)。
- 32行夹具只适合验证正确性、学习看计划。优化器在小表上选全表扫描不一定有问题，
  不能据此下结论“必须加索引”或宣称已完成性能验收。
- 后续约1万条专属合成数据时，再比较OFFSET 0与5000，保持其余条件相同且匹配总数>5000。
  当前31条有效数据的深页会在Service层跳过列表SQL；直接运行脚本E只是SQL操作示例。
- 性能对比不要额外附加用于隔离夹具的keyword，除非本来就在评估名称搜索；
  否则你测到的是另一种WHERE条件。
- 每组记录首轮及后续至少5次耗时、总数据量、匹配量、MySQL版本、机器资源。
  暂不新增索引，更不要FORCE INDEX掩盖问题。

## 7. 本轮交作业

先给出集合运行结果（39个请求）、A组默认分页的两张EXPLAIN结果，
以及B组状态+时间的两张EXPLAIN结果。附SHOW INDEX输出。
用自己的话回答：选了哪个索引？rows是多少？有没有额外排序？为什么COUNT和列表不同？
再决定要不要扩充数据做性能专项；本轮并未实际执行数据库验收或性能测试。

## 8. 可选清理

只在本次夹具所在隔离库分段执行 `03-cleanup.sql`，预览后确认再提交。
它按固定ID、名称/编码标记、created_by限定删除，不清空表、不删数据卷。
物理删除后只能重新运行seed恢复夹具；如发现额外SKU或其他关联，部分数据会保留，
不要扩展删除范围强行清干净。不要把清理文件放进Flyway或应用启动脚本。
