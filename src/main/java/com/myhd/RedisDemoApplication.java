package com.myhd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Description: ${NAME}
 * <br></br>
 * className: ${NAME}
 * <br></br>
 * packageName: com.myhd
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/5 10:13
 */
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.myhd.mapper")
@SpringBootApplication
public class RedisDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisDemoApplication.class);
    }
}