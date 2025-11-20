package com.military.asset.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * 解决 Spring Boot 3.x 兼容性问题，提供分页等插件支持

 * 位置：com.military.asset.config 包
 * 作用：配置 MyBatis-Plus 拦截器，增强数据库操作功能

 * 包含功能：
 * 1. 分页插件 - 支持前端分页查询
 * 2. 性能分析插件 - 开发环境可开启SQL性能分析
 * 3. 乐观锁插件 - 支持并发更新控制
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 添加 MyBatis-Plus 拦截器
     * 包含分页插件等核心功能
     *
     * @return MybatisPlusInterceptor 配置好的拦截器实例

     * 配置说明：
     * - PaginationInnerInterceptor: 分页插件，支持MySQL分页语法
     * - DbType.MYSQL: 指定数据库类型为MySQL
     * - 可根据需要添加其他插件（如乐观锁、性能分析等）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件（必须配置，否则分页功能无法使用）
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);

        // 分页插件配置
        paginationInterceptor.setMaxLimit(1000L);        // 单页分页条数限制
        paginationInterceptor.setOverflow(false);        // 页码超过总数时是否返回首页

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}