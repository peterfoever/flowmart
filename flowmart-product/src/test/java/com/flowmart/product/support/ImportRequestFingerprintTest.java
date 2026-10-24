package com.flowmart.product.support;

import com.flowmart.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ImportRequestFingerprintTest {
    private static final String HASH = "ab".repeat(32);

    @Test
    @DisplayName("v1固定向量防止算法修改后旧批次重试发生指纹漂移")
    void goldenVector() {
        assertEquals("a34c31b0dac055c5ee180749c1473bf26f309e7f81639f3873d7d67ae974a8ce",
                ImportRequestFingerprint.calculate(HASH, "v1", "{}"));
    }

    @Test
    @DisplayName("选项递归排序、JSON排版和哈希字母大小写不改变指纹")
    void stableAcrossFormatting() {
        String left = ImportRequestFingerprint.calculate(HASH, "v1",
                "{\"b\":2,\"a\":{\"y\":[1,2],\"x\":\"中文\"}}");
        String right = ImportRequestFingerprint.calculate(HASH.toUpperCase(), "v1",
                "{ \"a\": { \"x\":\"中文\", \"y\":[1.0,2.00]}, \"b\":2 }");
        assertEquals(left, right);
        assertTrue(left.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("文件、模板、选项改变均改变指纹")
    void includesEveryDecisionInput() {
        String base = ImportRequestFingerprint.calculate(HASH, "v1", "{}");
        assertNotEquals(base, ImportRequestFingerprint.calculate("cd".repeat(32), "v1", "{}"));
        assertNotEquals(base, ImportRequestFingerprint.calculate(HASH, "v2", "{}"));
        assertNotEquals(base, ImportRequestFingerprint.calculate(HASH, "v1", "{\"skip\":true}"));
    }

    @Test
    @DisplayName("数组顺序、字符串空白、JSON类型有业务意义，不能被归一掉")
    void preservesMeaningfulDifferences() {
        assertNotEquals(fingerprint("{\"a\":[1,2]}"), fingerprint("{\"a\":[2,1]}"));
        assertNotEquals(fingerprint("{\"a\":\" x\"}"), fingerprint("{\"a\":\"x\"}"));
        assertNotEquals(fingerprint("{\"a\":\"1\"}"), fingerprint("{\"a\":1}"));
        assertNotEquals(fingerprint("{\"a\":null}"), fingerprint("{}"));
        assertNotEquals(fingerprint("{\"a\":\"x:y\"}"), fingerprint("{\"a:x\":\"y\"}"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"[]", "null", "1", "true", "{", "{\"a\":1,\"a\":2}",
            "{\"a\":{\"x\":1,\"x\":2}}", "{} {}", "{\"a\":NaN}"})
    @DisplayName("非法选项、重复键、尾随JSON拒绝，不能静默丢数据")
    void rejectsInvalidOptions(String options) {
        assertThrows(BizException.class, () -> fingerprint(options));
    }

    @Test
    @DisplayName("超长和过深的选项拒绝")
    void rejectsOversizedOptions() {
        assertThrows(BizException.class, () -> fingerprint(" ".repeat(65_537)));
        assertThrows(BizException.class, () -> fingerprint("{\"a\":" + "[".repeat(101) + "0" + "]".repeat(101) + "}"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "gggg", " ab"})
    @DisplayName("文件哈希必须是完整SHA-256十六进制文本")
    void rejectsInvalidHash(String hash) {
        assertThrows(BizException.class, () -> ImportRequestFingerprint.calculate(hash, "v1", "{}"));
    }

    @Test
    @DisplayName("模板版本不能为空或超长，允许64位")
    void validatesTemplateVersion() {
        for (String version : new String[]{"", " ", "v".repeat(65)}) {
            assertThrows(BizException.class, () -> ImportRequestFingerprint.calculate(HASH, version, "{}"));
        }
        assertThrows(BizException.class, () -> ImportRequestFingerprint.calculate(HASH, null, "{}"));
        assertNotNull(ImportRequestFingerprint.calculate(HASH, "v".repeat(64), "{}"));
    }

    private String fingerprint(String options) {
        return ImportRequestFingerprint.calculate(HASH, "v1", options);
    }
}
