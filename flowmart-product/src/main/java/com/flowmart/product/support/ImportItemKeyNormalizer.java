package com.flowmart.product.support;

import cn.hutool.core.util.StrUtil;
import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/** 导入商品分组、持久化和跨文件防重共用的唯一规范化入口。 */
public final class ImportItemKeyNormalizer {
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private ImportItemKeyNormalizer() { }

    /**
     * 清理首尾空白（包括全角空格），校验 ASCII 字符后转大写。
     * 不压缩内部空白、不截断、不转换非法 Unicode 字符，避免不同来源标识意外合并。
     * @throws BizException 标识为空、超长或包含非法字符
     */
    public static String normalize(String rawKey) {
        String key = StrUtil.trim(rawKey);
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "导入商品标识须为1～64位字母、数字、下划线或连字符");
        }
        return key.toUpperCase(Locale.ROOT);
    }
}
