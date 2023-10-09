package com.myhd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Description: GlobalBeanConfig
 * <br></br>
 * className: GlobalBeanConfig
 * <br></br>
 * packageName: com.myhd.config
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/8 14:30
 */
@Configuration
public class GlobalBeanConfig {

    /**
     * Description: random 全局通用的单例Random
     * @return java.util.Random
     * @author jinhui-huang
     * @Date 2023/10/8
     * */
    @Bean
    public Random random() {
        return new Random();
    }

    /**
     * Description: cacheRebuildExecutor 用于开启独立线程的线程池
     * @return java.util.concurrent.ExecutorService
     * @author jinhui-huang
     * @Date 2023/10/8
     * */
    @Bean
    public ExecutorService cacheRebuildExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
