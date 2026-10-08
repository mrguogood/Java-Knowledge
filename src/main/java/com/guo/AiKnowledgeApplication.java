package com.guo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 项目启动类
 */
@SpringBootApplication
@MapperScan("com.guo.mapper")
public class AiKnowledgeApplication {
    public static void main(String[] args) {
        System.setProperty("csp.sentinel.log.dir", "D:/logs/sentinel");
        SpringApplication.run(AiKnowledgeApplication.class, args);
    }
}