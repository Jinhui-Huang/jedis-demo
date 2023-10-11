package com.myhd.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description: RedissonConfig
 * <br></br>
 * className: RedissonConfig
 * <br></br>
 * packageName: com.myhd.config
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/11 21:36
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        /*配置*/
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.43.33:6379").setPassword("12345678hjh");
        /*创建RedissonClient对象*/
        return Redisson.create(config);
    }
}
