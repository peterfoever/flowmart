package com.flowmart.product.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.flowmart.product.entity.ImportGroupResultEntity;
import java.time.LocalDateTime;

public final class ImportTexts {
    private ImportTexts() {}
    /** 仅用于报告展示，不用于业务字段或身份匹配；避免截断代理对。 */
    public static String clip(String value, int max) {
        if (value == null || value.length() <= max) return value;
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }
    public static ImportGroupResultEntity result(Long batch, Long actor, String rawKey, String key, String name, int count) {
        var result = new ImportGroupResultEntity();
        result.setId(IdWorker.getId()); result.setBatchId(batch); result.setImportItemKey(clip(cn.hutool.core.util.StrUtil.trim(rawKey), 64));
        result.setImportItemKeyNormalized(key); result.setSpuName(clip(name, 128)); result.setRowCount(count);
        result.setCreatedBy(actor); result.setUpdatedBy(actor);
        result.setCreatedAt(LocalDateTime.now()); result.setUpdatedAt(result.getCreatedAt());
        return result;
    }
}
