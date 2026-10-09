-- FM-005: 在IDEA数据库Query Console中选中一条SQL执行，而非在Postman中运行。
-- 这些语句都是SELECT/EXPLAIN，不新增索引、不写业务数据。
USE flowmart_test;
SELECT DATABASE(),VERSION(),@@session.time_zone;
SHOW INDEX FROM product_spu;

-- 普通EXPLAIN是估算计划。MySQL8.0.18+可将下面某条EXPLAIN替换为EXPLAIN ANALYZE。
-- ANALYZE会实际执行SELECT，不能拿本脚本直接对生产或未知大表跑。
-- cost不是毫秒；ANALYZE actual time单位才是毫秒。
-- 下列COUNT/列表不保证走同一个索引；应分别保存输出。

-- A 默认分页（不要为了隔离夹具添加keyword，这会改变执行计划）
EXPLAIN
SELECT COUNT(1) FROM product_spu spu
WHERE spu.deleted = 0;

EXPLAIN
SELECT spu.id, spu.name, spu.spu_code AS spuCode,
       spu.category_id AS categoryId, spu.brand_id AS brandId,
       spu.main_image_url AS mainImageUrl, spu.version,
       c.name AS categoryName, b.name AS brandName, spu.status,
       spu.created_at AS createdAt, spu.updated_at AS updatedAt
FROM product_spu spu
LEFT JOIN product_category c ON c.id = spu.category_id AND c.deleted = 0
LEFT JOIN product_brand b ON b.id = spu.brand_id AND b.deleted = 0
WHERE spu.deleted = 0
ORDER BY spu.created_at DESC, spu.id DESC
LIMIT 20 OFFSET 0;

-- B 状态0 + 10月7日时间区间
EXPLAIN
SELECT COUNT(1) FROM product_spu spu
WHERE spu.deleted = 0 AND spu.status = 0
 AND spu.created_at >= '2026-10-07 00:00:00'
 AND spu.created_at < '2026-10-08 00:00:00';

EXPLAIN
SELECT spu.id, spu.name, spu.spu_code AS spuCode,
       spu.category_id AS categoryId, spu.brand_id AS brandId,
       spu.main_image_url AS mainImageUrl, spu.version,
       c.name AS categoryName, b.name AS brandName, spu.status,
       spu.created_at AS createdAt, spu.updated_at AS updatedAt
FROM product_spu spu
LEFT JOIN product_category c ON c.id = spu.category_id AND c.deleted = 0
LEFT JOIN product_brand b ON b.id = spu.brand_id AND b.deleted = 0
WHERE spu.deleted = 0 AND spu.status = 0
 AND spu.created_at >= '2026-10-07 00:00:00'
 AND spu.created_at < '2026-10-08 00:00:00'
ORDER BY spu.created_at DESC, spu.id DESC
LIMIT 20 OFFSET 0;

-- C 数码父类目（CTE展开结果为根节点+两个子节点）
EXPLAIN
SELECT COUNT(1) FROM product_spu spu
WHERE spu.deleted = 0 AND spu.category_id IN (9100051000,9100051001,9100051002);

EXPLAIN
SELECT spu.id, spu.name, spu.spu_code AS spuCode,
       spu.category_id AS categoryId, spu.brand_id AS brandId,
       spu.main_image_url AS mainImageUrl, spu.version,
       c.name AS categoryName, b.name AS brandName, spu.status,
       spu.created_at AS createdAt, spu.updated_at AS updatedAt
FROM product_spu spu
LEFT JOIN product_category c ON c.id = spu.category_id AND c.deleted = 0
LEFT JOIN product_brand b ON b.id = spu.brand_id AND b.deleted = 0
WHERE spu.deleted = 0 AND spu.category_id IN (9100051000,9100051001,9100051002)
ORDER BY spu.created_at DESC, spu.id DESC
LIMIT 20 OFFSET 0;

-- D 名称按字面量搜索100%（查询值已经按服务端规则转义）
EXPLAIN
SELECT COUNT(1) FROM product_spu spu
WHERE spu.deleted = 0 AND spu.name LIKE '%FM005QA20261009 折扣100!%%' ESCAPE '!';

EXPLAIN
SELECT spu.id, spu.name, spu.spu_code AS spuCode,
       spu.category_id AS categoryId, spu.brand_id AS brandId,
       spu.main_image_url AS mainImageUrl, spu.version,
       c.name AS categoryName, b.name AS brandName, spu.status,
       spu.created_at AS createdAt, spu.updated_at AS updatedAt
FROM product_spu spu
LEFT JOIN product_category c ON c.id = spu.category_id AND c.deleted = 0
LEFT JOIN product_brand b ON b.id = spu.brand_id AND b.deleted = 0
WHERE spu.deleted = 0 AND spu.name LIKE '%FM005QA20261009 折扣100!%%' ESCAPE '!'
ORDER BY spu.created_at DESC, spu.id DESC
LIMIT 20 OFFSET 0;

-- E 深页（32行只能学操作，性能实验需约1万条数据且total>offset）
EXPLAIN
SELECT COUNT(1) FROM product_spu spu
WHERE spu.deleted = 0;

EXPLAIN
SELECT spu.id, spu.name, spu.spu_code AS spuCode,
       spu.category_id AS categoryId, spu.brand_id AS brandId,
       spu.main_image_url AS mainImageUrl, spu.version,
       c.name AS categoryName, b.name AS brandName, spu.status,
       spu.created_at AS createdAt, spu.updated_at AS updatedAt
FROM product_spu spu
LEFT JOIN product_category c ON c.id = spu.category_id AND c.deleted = 0
LEFT JOIN product_brand b ON b.id = spu.brand_id AND b.deleted = 0
WHERE spu.deleted = 0
ORDER BY spu.created_at DESC, spu.id DESC
LIMIT 20 OFFSET 5000;

-- C场景还会执行类目存在性校验与递归查询；不要只看SPU分页SQL。
EXPLAIN
WITH RECURSIVE category_tree AS (
 SELECT id,parent_id,level,name FROM product_category
 WHERE id=9100051000 AND deleted=0
 UNION ALL
 SELECT c.id,c.parent_id,c.level,c.name
 FROM product_category c JOIN category_tree t ON c.parent_id=t.id
 WHERE c.deleted=0
)
SELECT id,parent_id,level,name FROM category_tree;

-- 保存证据模板（每个场景COUNT、列表分开填写）：
-- MySQL版本/CPU/内存/表行数/过滤条件/limit/offset：
-- type / possible_keys / key / rows / filtered / Extra：
-- EXPLAIN ANALYZE根节点actual time、rows、loops：
-- 首次耗时及后续至少5次耗时（不要把子节点包含时间相加）：
-- 请求SQL总条数及Postman耗时（与SQL耗时分开）：
-- 是否需要优化、依据、下一步：
