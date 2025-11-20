package com.military.asset;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * 军工资产系统启动类
 * 核心作用：启动Spring容器并加载所有组件

 * 修改说明：
 * 1. 移除 MyBatisAutoConfiguration 排除（已在 application.yml 中移除）
 * 2. 确保 @MapperScan 路径正确
 */
@SpringBootApplication
// 扫描Mapper接口所在包（必须与你的mapper包路径一致）
@MapperScan("com.military.asset.mapper")
@Slf4j
public class MilitaryAssetApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(MilitaryAssetApplication.class, args);
            // 启动成功日志（包含明确提示）
            log.info("✅ 系统启动成功！访问地址：http://localhost:8080");
            log.info("✅ 软件资产导入接口：POST http://localhost:8080/api/asset/import/software");
            log.info("✅ 网信资产导入接口：POST http://localhost:8080/api/asset/import/cyber");
            log.info("✅ 数据资产导入接口：POST http://localhost:8080/api/asset/import/data-content");
            log.info("✅ CRUD接口前缀：http://localhost:8080/api/asset");
        } catch (Exception e) {
            // 关键：打印完整异常堆栈，包含导致启动失败的具体原因
            log.error("❌ 启动失败！详细错误：", e);
            log.error("🔍 排查建议：");
            log.error("1. 确认 MySQL 服务已启动（默认端口3306）");
            log.error("2. 确认数据库 military_asset_db 已创建");
            log.error("3. 确认 application.yml 中的数据库用户名密码正确");
            log.error("4. 确认所有 Mapper 接口已移除 @Mapper 注解");
            log.error("5. 确认 resources/mapper 目录下存在对应的 XML 文件");
            System.exit(1); // 启动失败后退出进程
        }
    }
}