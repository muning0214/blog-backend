package com.devlog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DevLog 博客后端启动类。
 *
 * 三模块结构里的「后端」部分：负责鉴权、校验、事务与文件存储。
 * 数据库结构不在这里维护，见 blog-database/01_schema.sql。
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.devlog.config")
@MapperScan("com.devlog.mapper")
public class DevLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevLogApplication.class, args);
    }
}
