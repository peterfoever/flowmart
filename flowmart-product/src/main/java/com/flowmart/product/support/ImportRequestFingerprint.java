package com.flowmart.product.support;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 请求内容指纹 v1：文件哈希 + 模板版本 + 规范化选项，不包含请求幂等键。 */
public final class ImportRequestFingerprint {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MAX_OPTIONS_LENGTH = 65_536;
    // 独立配置，不能改变应用全局 JSON 行为。重复键和尾随 JSON 必须拒绝。
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(100).build())
            .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private ImportRequestFingerprint() { }

    /**
     * optionsJson 必须是 JSON 对象，无选项传 "{}"。
     * 对象键递归排序，数组保持顺序，数字去除无意义尾零；字符串内容不做清洗。
     * 模板版本逐字比较，由服务端提供固定版本。算法改变时必须显式升级，不能静默改 v1。
     * @throws BizException 输入不合法
     */
    public static String calculate(String fileHash, String templateVersion, String optionsJson) {
        if (fileHash == null || !SHA256.matcher(fileHash).matches()) {
            throw invalid("文件哈希必须为64位十六进制SHA-256");
        }
        if (templateVersion == null || templateVersion.isBlank() || templateVersion.length() > 64) {
            throw invalid("模板版本不能为空且不能超过64个字符");
        }
        if (optionsJson == null || optionsJson.length() > MAX_OPTIONS_LENGTH) {
            throw invalid("导入选项必须为JSON对象且不能超过65536个字符");
        }
        try {
            JsonNode options = JSON.readTree(optionsJson);
            if (options == null || !options.isObject()) {
                throw invalid("导入选项必须为JSON对象");
            }
            var payload = JSON.createObjectNode();
            payload.put("fingerprintVersion", 1);
            payload.put("fileHash", fileHash.toLowerCase(Locale.ROOT));
            payload.put("templateVersion", templateVersion);
            payload.set("options", options);
            StringBuilder canonical = new StringBuilder();
            appendCanonical(canonical, payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException e) {
            throw invalid("导入选项JSON不合法，不能包含重复键或多份JSON");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private static void appendCanonical(StringBuilder out, JsonNode node) {
        if (node.isObject()) {
            out.append('{');
            var keys = new TreeSet<String>();
            node.fieldNames().forEachRemaining(keys::add);
            boolean first = true;
            for (String key : keys) {
                if (!first) out.append(',');
                first = false;
                out.append(JSON.getNodeFactory().textNode(key)).append(':');
                appendCanonical(out, node.get(key));
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) out.append(',');
                appendCanonical(out, node.get(i));
            }
            out.append(']');
        } else if (node.isNumber()) {
            out.append(node.decimalValue().stripTrailingZeros().toString());
        } else {
            out.append(node);
        }
    }

    private static BizException invalid(String message) {
        return new BizException(CommonErrorCode.PARAM_INVALID, message);
    }
}
