package com.flowmart.product.support;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ImportItemKeyNormalizerTest {
    @Test
    @DisplayName("首尾半角全角空白被清理，同一标识大小写归为同一组")
    void normalizesWhitespaceAndCase() {
        assertEquals("ITEM_001-A", ImportItemKeyNormalizer.normalize(" \t\u3000item_001-a\u3000\n"));
        assertEquals(ImportItemKeyNormalizer.normalize("item-001"),
                ImportItemKeyNormalizer.normalize("ITEM-001"));
    }

    @Test
    @DisplayName("64位边界合法且规范化幂等，不受默认语言环境影响")
    void boundaryAndIdempotence() {
        assertEquals("I".repeat(64), ImportItemKeyNormalizer.normalize("i".repeat(64)));
        String value = ImportItemKeyNormalizer.normalize("item-1");
        assertEquals(value, ImportItemKeyNormalizer.normalize(value));
        // ASCII i 必须变为 I，不能采用土耳其语言的带点大写字母。
        assertEquals("I", ImportItemKeyNormalizer.normalize("i"));
    }

    @Test
    @ResourceLock("java.util.Locale")
    @DisplayName("土耳其默认语言环境不能改变ASCII标识规范化结果")
    void ignoresDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("ITEM-I", ImportItemKeyNormalizer.normalize("item-i"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u3000", "a b", "a\tb", "a\u3000b", "商品1", "a/b", "a:b", "ß", "ａ"})
    @DisplayName("空标识和非法字符拒绝，不静默合并标识")
    void rejectsInvalidKeys(String key) {
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> ImportItemKeyNormalizer.normalize(key)).getCode());
    }

    @Test
    @DisplayName("超过64位拒绝而不是截断")
    void rejectsTooLong() {
        assertThrows(BizException.class, () -> ImportItemKeyNormalizer.normalize("a".repeat(65)));
    }
}
