package com.military.asset.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置类
 * 解决前端（localhost:5173）访问后端（localhost:8080）的跨域问题
 */
@Configuration
@SuppressWarnings("unused")  // 添加这行来抑制警告
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许所有域名进行跨域调用（生产环境应指定具体域名）
        config.addAllowedOriginPattern("*");

        // 允许跨越发送cookie
        config.setAllowCredentials(true);

        // 放行全部原始头信息
        config.addAllowedHeader("*");

        // 允许所有请求方法跨域调用
        config.addAllowedMethod("*");

        // 添加暴露的头信息
        config.addExposedHeader("Content-Disposition");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}