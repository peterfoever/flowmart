package com.flowmart.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 * <p>
 * 业务代码里不要手写 {@code setCreatedAt(now())} —— 漏写一处就是一处数据缺失。
 * <p>
 * TODO(Week 5 鉴权需求): 接入登录上下文后，把 {@link #currentUserId()} 换成从
 * ThreadLocal 里取真实操作人，而不是现在的系统账号。
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /** 系统账号，无登录上下文时兜底（定时任务、数据初始化等场景） */
    private static final Long SYSTEM_USER_ID = 0L;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", Long.class, userId);
        strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        strictInsertFill(metaObject, "deleted", Long.class, 0L);
        strictInsertFill(metaObject, "version", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId());
    }

    private Long currentUserId() {
        return SYSTEM_USER_ID;
    }
}
