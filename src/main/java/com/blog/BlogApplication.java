package com.blog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Personal Blog Backend Application Entry Point.
 */
@SpringBootApplication
@MapperScan("com.blog.mapper")
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
        System.out.println("====================================");
        System.out.println(" Blog Backend started successfully!");
        System.out.println(" Access: http://localhost:8080");
        System.out.println("====================================");
    }
}
