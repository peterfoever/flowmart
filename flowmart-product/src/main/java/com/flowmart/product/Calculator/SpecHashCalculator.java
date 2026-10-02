package com.flowmart.product.Calculator;

import cn.hutool.core.util.StrUtil;
import com.flowmart.common.exception.BizException;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.enums.ProductErrorCode;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 规格组合哈希计算器
 */
public final class SpecHashCalculator {
    private SpecHashCalculator() {}

    public static String calculate(List<SkuSpecValueDTO> specValues) {
        String canonical = canonicalize(specValues);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String canonicalize(List<SkuSpecValueDTO> specValues) {
        if (specValues == null || specValues.isEmpty()) return "{}";

        // 先拷贝 + trim，不碰原始 DTO
        List<Map.Entry<String, String>> entries = new ArrayList<>(specValues.size());
        for (SkuSpecValueDTO dto : specValues) {
            if (dto == null) {
                throw new BizException(ProductErrorCode.SPEC_VALUES_EMPTY);
            }
            String name = StrUtil.trim(dto.getName());
            String value = StrUtil.trim(dto.getValue());
            entries.add(new AbstractMap.SimpleImmutableEntry<>(name, value));
        }

        // trim 后检查规格名是否重复
        Set<String> seenNames = new HashSet<>();
        for (Map.Entry<String, String> entry : entries) {
            if (!seenNames.add(entry.getKey())) {
                throw new BizException(ProductErrorCode.SPEC_NAME_DUPLICATE);
            }
        }

        // 规则：只对哈希副本排序，不修改原列表
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : entries) {
            appendField(sb, e.getKey());
            appendField(sb, e.getValue());
        }
        return sb.toString();
    }

    /** 长度前缀用 UTF-8 字节数，不是 String.length()。 */
    private static void appendField(StringBuilder sb, String s) {
        int byteLength = s.getBytes(StandardCharsets.UTF_8).length;
        sb.append(byteLength).append(':').append(s);
    }
}
