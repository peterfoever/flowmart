-- FM-005 手工验收夹具；仅在个人隔离测试库运行，不是 Flyway migration。
-- 默认 flowmart_test（通常127.0.0.1:3307，应用8081）。连接和应用必须指向同一库。
-- 若要使用其他个人测试库，请先核对数据源再显式修改 USE；严禁生产执行。
-- 固定ID+唯一前缀保证可定位；重复执行会报重复键，不使用 IGNORE/REPLACE 覆盖数据。
USE flowmart_test;
SELECT DATABASE() AS current_database, VERSION() AS mysql_version,
       @@session.time_zone AS session_time_zone;

-- 第一步：单独执行预检，四项必须全为0。否则停止，不要覆盖已有数据。
SELECT
 (SELECT COUNT(*) FROM product_spu
   WHERE id BETWEEN 9100050001 AND 9100050032
      OR spu_code LIKE 'FM005QA20261009-%') AS spu_conflicts,
 (SELECT COUNT(*) FROM product_category
   WHERE id IN (9100051000,9100051001,9100051002,9100051100,9100051101,
                9100051102,9100051103,9100051199)
      OR name LIKE 'FM005QA20261009%') AS category_conflicts,
 (SELECT COUNT(*) FROM product_brand
   WHERE id IN (9100052001,9100052002,9100052003,9100052099)
      OR name LIKE 'FM005QA20261009%') AS brand_conflicts,
 (SELECT COUNT(*) FROM product_category_brand
   WHERE id BETWEEN 9100053001 AND 9100053004) AS binding_conflicts;

-- 第二步：确认预检为0后执行下面事务。任意报错立即 ROLLBACK，不能继续 COMMIT。
START TRANSACTION;

INSERT INTO product_category
 (id,parent_id,name,level,status,deleted,created_by,updated_by)
VALUES
 (9100051000,0,'FM005QA20261009-数码',1,1,0,910005,910005),
 (9100051001,9100051000,'FM005QA20261009-手机',2,1,0,910005,910005),
 (9100051002,9100051000,'FM005QA20261009-电脑',2,1,0,910005,910005),
 (9100051100,0,'FM005QA20261009-食品',1,1,0,910005,910005),
 (9100051101,9100051100,'FM005QA20261009-零食',2,1,0,910005,910005),
 (9100051102,9100051100,'FM005QA20261009-禁用类目',2,0,0,910005,910005),
 (9100051103,9100051100,'FM005QA20261009-已删除类目',2,1,9100051103,910005,910005);

INSERT INTO product_brand
 (id,name,initial,status,deleted,created_by,updated_by)
VALUES
 (9100052001,'FM005QA20261009-品牌A','F',1,0,910005,910005),
 (9100052002,'FM005QA20261009-禁用品牌','F',0,0,910005,910005),
 (9100052003,'FM005QA20261009-已删除品牌','F',1,9100052003,910005,910005);

-- 正常品牌绑定。失效关联只用于模拟历史脏数据，不能通过创建接口构造。
INSERT INTO product_category_brand
 (id,category_id,brand_id,created_by,updated_by,deleted)
VALUES
 (9100053001,9100051001,9100052001,910005,910005,0),
 (9100053002,9100051002,9100052001,910005,910005,0),
 (9100053003,9100051001,9100052002,910005,910005,0),
 (9100053004,9100051001,9100052003,910005,910005,9100053004);

-- 一条 INSERT 生成32行：JSON列显式写入[]；状态/失效关联为查询专用夹具。
-- 不代表完成SKU生成或上架流程。请勿拿这批商品测试写接口。
INSERT INTO product_spu
 (id,spu_code,name,category_id,brand_id,main_image_url,carousel_images,spec_json,
  description,status,created_by,created_at,updated_by,updated_at,deleted,version)
SELECT
 9100050000+n,
 CONCAT('FM005QA20261009-',LPAD(n,3,'0')),
 CONCAT('FM005QA20261009 ',
   CASE n
    WHEN 7 THEN '折扣100%手机'
    WHEN 8 THEN '折扣100X手机'
    WHEN 9 THEN '型号A_B'
    WHEN 10 THEN '型号AXB'
    WHEN 11 THEN '促销!特别款'
    WHEN 12 THEN '促销普通款'
    ELSE CONCAT('商品',LPAD(n,3,'0'))
   END),
 CASE WHEN n<=12 OR n=32 THEN 9100051001
      WHEN n<=22 THEN 9100051002
      WHEN n BETWEEN 23 AND 26 OR n=31 THEN 9100051101
      WHEN n IN (27,28) THEN 9100051102
      WHEN n=29 THEN 9100051103 ELSE 9100051199 END,
 CASE WHEN n=3 OR n BETWEEN 23 AND 28 OR n=31 THEN NULL
      WHEN n=4 THEN 9100052002
      WHEN n=5 THEN 9100052003
      WHEN n=6 THEN 9100052099 ELSE 9100052001 END,
 'https://example.com/fm005-test.png',JSON_ARRAY(),JSON_ARRAY(),
 'FM005QA20261009: 仅供分页查询验收，不测试业务写接口',
 MOD(n-1,3),910005,
 CASE n WHEN 1 THEN '2026-10-06 23:59:59.999'
        WHEN 2 THEN '2026-10-07 00:00:00.000'
        WHEN 3 THEN '2026-10-07 23:59:59.999'
        WHEN 4 THEN '2026-10-08 00:00:00.000'
        ELSE '2026-10-07 12:00:00.000' END,
 910005,'2026-10-09 12:00:00.000',
 CASE WHEN n=31 THEN 9100050031 ELSE 0 END,0
FROM (
 SELECT tens.d*10+ones.d+1 AS n
 FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) tens
 CROSS JOIN
 (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
) sequence_numbers
WHERE n<=32;

-- 第三步：同一控制台检查，预期 inserted_rows=32, visible_rows=31。
SELECT COUNT(*) AS inserted_rows,SUM(deleted=0) AS visible_rows
FROM product_spu WHERE spu_code LIKE 'FM005QA20261009-%';
-- 预期status0=10, status1=11, status2=10。
SELECT status,COUNT(*) AS visible_rows
FROM product_spu WHERE spu_code LIKE 'FM005QA20261009-%' AND deleted=0
GROUP BY status ORDER BY status;

-- 第四步：检查无误后单独执行 COMMIT; 此前其他连接/Postman看不到这批数据。
-- COMMIT;
-- 如有错误或不想保留则单独执行 ROLLBACK;（不要与COMMIT一起运行）
