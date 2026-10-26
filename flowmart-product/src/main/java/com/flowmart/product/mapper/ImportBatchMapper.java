package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.entity.ImportBatchEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ImportBatchMapper extends BaseMapper<ImportBatchEntity> {
    @Select("SELECT status FROM import_batch WHERE id = #{id} AND owner_id = #{ownerId} FOR UPDATE")
    String lockStatus(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Update("UPDATE import_batch SET updated_at = #{now} WHERE id = #{id}")
    int heartbeat(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}
