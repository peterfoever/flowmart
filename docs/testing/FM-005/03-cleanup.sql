-- 可选清理：只针对01-seed.sql的专属夹具。执行后无法恢复，需重新跑seed。
-- 只能在原来的隔离测试库运行；若测试期间新增了关联数据，停止并人工确认。
USE flowmart_test;
START TRANSACTION;

-- 先预览，确认全部是本次夹具。
SELECT id,spu_code,name FROM product_spu
WHERE id BETWEEN 9100050001 AND 9100050032
  AND spu_code LIKE 'FM005QA20261009-%' AND created_by=910005;

-- 若意外给这批SPU生成过SKU，父SPU不会被删，后续相关类目/品牌也会保留。
DELETE spu FROM product_spu spu
WHERE spu.id BETWEEN 9100050001 AND 9100050032
  AND spu.spu_code LIKE 'FM005QA20261009-%' AND spu.created_by=910005
  AND NOT EXISTS (SELECT 1 FROM product_sku sku WHERE sku.spu_id=spu.id);

DELETE FROM product_category_brand
WHERE id BETWEEN 9100053001 AND 9100053004 AND created_by=910005
  AND category_id IN (9100051001,9100051002)
  AND brand_id IN (9100052001,9100052002,9100052003);

-- 仅删除没有任何SPU/绑定引用的测试品牌。
DELETE b FROM product_brand b
WHERE b.id IN (9100052001,9100052002,9100052003)
  AND b.created_by=910005 AND b.name LIKE 'FM005QA20261009-%'
  AND NOT EXISTS (SELECT 1 FROM product_spu s WHERE s.brand_id=b.id)
  AND NOT EXISTS (SELECT 1 FROM product_category_brand cb WHERE cb.brand_id=b.id);

-- 叶子类目先清理，存在额外引用/子节点则保留。
DELETE c FROM product_category c
LEFT JOIN product_category child ON child.parent_id=c.id
WHERE c.id IN (9100051001,9100051002,9100051101,9100051102,9100051103)
  AND c.created_by=910005 AND c.name LIKE 'FM005QA20261009-%'
  AND child.id IS NULL
  AND NOT EXISTS (SELECT 1 FROM product_spu s WHERE s.category_id=c.id)
  AND NOT EXISTS (SELECT 1 FROM product_category_brand cb WHERE cb.category_id=c.id);

DELETE c FROM product_category c
LEFT JOIN product_category child ON child.parent_id=c.id
WHERE c.id IN (9100051000,9100051100)
  AND c.created_by=910005 AND c.name LIKE 'FM005QA20261009-%'
  AND child.id IS NULL
  AND NOT EXISTS (SELECT 1 FROM product_spu s WHERE s.category_id=c.id)
  AND NOT EXISTS (SELECT 1 FROM product_category_brand cb WHERE cb.category_id=c.id);

SELECT id,spu_code FROM product_spu WHERE spu_code LIKE 'FM005QA20261009-%';
SELECT id,name FROM product_category WHERE name LIKE 'FM005QA20261009-%';
SELECT id,name FROM product_brand WHERE name LIKE 'FM005QA20261009-%';
-- 确认清理结果后单独COMMIT；否则ROLLBACK。不要两条一起执行。
-- COMMIT;
