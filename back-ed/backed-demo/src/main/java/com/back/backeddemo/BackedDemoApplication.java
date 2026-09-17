package com.back.backeddemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.back.backeddemo.mapper")
public class BackedDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackedDemoApplication.class, args);
    }

}
