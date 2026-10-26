package com.flowmart.product.support;

import java.sql.SQLException;
import java.util.regex.Pattern;

/** 只识别MySQL明确报告的目标唯一索引，不能把其他数据冲突当成可恢复业务失败。 */
public final class ImportSqlErrors {
    private ImportSqlErrors() {}
    public static boolean duplicateOn(Throwable error, String index) {
        Pattern key = Pattern.compile("for key ['`](?:[^'`]+\\.)?" + Pattern.quote(index) + "['`]");
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062
                    && "23000".equals(sql.getSQLState()) && sql.getMessage() != null
                    && key.matcher(sql.getMessage()).find()) return true;
        }
        return false;
    }
}
