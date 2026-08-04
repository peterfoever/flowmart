package com.flowmart.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件链。
 * <p>
 * 注意插件顺序：分页必须在乐观锁之前，防全表更新必须放最后。顺序错了会导致插件失效，
 * 这是 MyBatis-Plus 的经典坑。
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数硬上限，防止有人绕过 PageQuery 校验直接构造 Page 对象 */
    private static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        pagination.setOverflow(false); // 页码超出总页数时返回空列表，而不是回到第一页
        interceptor.addInnerInterceptor(pagination);

        // 更新时自动带上 version 条件，影响行数为 0 说明被并发改过
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 拦截没有 where 条件的 update/delete —— 防止一行代码清空整张表
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
