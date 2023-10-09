package com.myhd.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static com.myhd.utils.RedisConstants.*;

/**
 * Description: CacheClient
 * <br></br>
 * className: CacheClient
 * <br></br>
 * packageName: com.myhd.utils
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/8 20:23
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private final ExecutorService cacheRebuildExecutor;


    public CacheClient(StringRedisTemplate stringRedisTemplate, ExecutorService cacheRebuildExecutor) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheRebuildExecutor = cacheRebuildExecutor;
    }

    /**
     * Description: set 设置key string缓存格式
     *
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    public void set(String key, Object value, Duration duration) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), duration);
    }

    public String getJson(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * Description: setWithLogicExpire 普通的设置逻辑过期
     *
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    public void setWithLogicExpire(String key, Object value, Duration duration) {
        /*设置逻辑过期*/
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(duration.getSeconds()));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * Description: 将数据存入Redis同时设置逻辑过期时间, 能够解决缓存穿透问题
     *
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    public <R, ID> void saveShop2Redis(String keyPrefix, ID id, Function<ID, R> dbFallback, Duration duration) {
        String key = keyPrefix + id;
        /*设置逻辑过期*/
        R r = dbFallback.apply(id);
        if (r == null) {
            this.set(key, "", Duration.ofMinutes(CACHE_NULL_TTL));
            return;
        }
        /*不为空缓存数据*/
        /*封装存储数据和逻辑过期时间*/
        this.setWithLogicExpire(key, r, duration);
    }

    /**
     * Description: 查询数据同时解决缓存穿透问题, 但不能解决缓存击穿问题
     *
     * @return R
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> clazz, Function<ID, R> dbFallback, Duration duration) {
        String key = keyPrefix + id;
        String json = this.getJson(key);
        if (StringUtils.isNotBlank(json)) {
            return JSONUtil.toBean(json, clazz);
        }

        /*json为非null的 blank字符串*/
        if (json != null) {
            return null;
        }

        R r = dbFallback.apply(id);

        if (r == null) {
            /*防止缓存穿透, 需要将空值""字符串写入*/
            this.set(key, "", Duration.ofMinutes(CACHE_NULL_TTL));
            return null;
        }
        /*存在写入Redis*/
        this.set(key, r, duration);
        return r;
    }

    /**
     * Description: 查询数据同时解决缓存穿透问题, 包括缓存击穿问题, 基于互斥锁方案
     *
     * @return R
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    public <R, ID> R queryWithPassThroughLock(String keyPrefix, String lockKeyPrefix, ID id, Class<R> clazz, Function<ID, R> dbFallback, Duration duration) {
        String key = keyPrefix + id;

        String json = this.getJson(key);

        if (StringUtils.isNotBlank(json)) {
            return JSONUtil.toBean(json, clazz);
        }

        /*json为非null的 blank字符串*/
        if (json != null) {
            return null;
        }

        /*不存在为null, 则根据id查询数据库*/
        /*TODO 缓存击穿: 这里需要添加互斥锁, 防止缓存击穿*/
        /*获取互斥锁*/
        String lockKey = lockKeyPrefix + id;
        R r;
        try {
            /*4.2. 判断是否获取成功*/
            if (!tryLock(lockKey)) {
                /*4.3. 失败, 则休眠50ms重试*/
                Thread.sleep(50);
                return queryWithPassThroughLock(keyPrefix, lockKeyPrefix, id, clazz, dbFallback, duration);
            }
            /*TODO 获取锁成功应该再次检测Redis缓存是否存在, 做DoubleCheck. 如果存在则无需重建缓存*/
            json = this.getJson(key);
            /*TODO 二次判断缓存是否存在*/
            if (StringUtils.isNotBlank(json)) {
                return JSONUtil.toBean(json, clazz);
            }
            /*json为非null的 blank字符串*/
            if (json != null) {
                return null;
            }

            /*获取锁成功开始重建缓存*/
            r = dbFallback.apply(id);
            if (r == null) {
                /*防止缓存穿透, 需要将空值""字符串写入*/
                this.set(key, "", Duration.ofMinutes(CACHE_NULL_TTL));
                return null;
            }
            /*存在写入Redis*/
            this.set(key, r, duration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            /*7. 释放互斥锁*/
            unlock(lockKey);
        }
        return r;
    }


    /**
     * Description: 查询数据同时解决缓存穿透问题, 包括缓存击穿问题, 基于逻辑过期方案
     *
     * @return R
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    public <R, ID> R queryWithPassThroughLogic(String keyPrefix, String lockKeyPrefix, ID id, Class<R> clazz, Function<ID, R> dbFallback, Duration duration) {
        String key = keyPrefix + id;
        String json = this.getJson(key);
        /*2. 判断是否命中, json为""*/
        if (json != null && StringUtils.isBlank(json)) {
            return null;
        }

        /*json为null的时候*/
        if (json == null) {
            /*更根据数据库判断是存入空值还是非空值*/
            this.saveShop2Redis(keyPrefix, id, dbFallback, duration);
            /*TODO 重新查询缓存, ""在字符串在上面返回, 下方是不为null有数据的json串*/
            return queryWithPassThroughLogic(keyPrefix, lockKeyPrefix, id, clazz, dbFallback, duration);
        }

        /*命中*/
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R data = redisData.getData(clazz);

        /*5. 判断是否过期*/
        if (expireTime.isAfter(LocalDateTime.now())) {
            /*5.1 未过期, 直接返回店铺信息*/
            return data;
        }

        /*5.2 TODO 已过期, 需要重建缓存, 这里是核心代码需要判断的地方*/
        /*6. 缓存重建*/
        /*6.1. 获取互斥锁*/
        String lockKey = lockKeyPrefix + id;
        /*6.3. TODO 成功, 开启独立线程, 实现缓存重建*/
        if (tryLock(lockKey)) {
            /*TODO 获取锁成功应该再次检测Redis缓存是否存在, 做DoubleCheck. 如果存在则无需重建缓存*/
            json = this.getJson(key);
            if (StringUtils.isBlank(json) && json != null) {
                return null;
            }
            if (json == null) {
                this.saveShop2Redis(keyPrefix, id, dbFallback, duration);
                return queryWithPassThroughLogic(keyPrefix, lockKeyPrefix, id, clazz, dbFallback, duration);
            }
            /*TODO 成功, 开启独立线程, 实现缓存重建*/
            cacheRebuildExecutor.submit(() -> {
                try {
                    this.saveShop2Redis(keyPrefix, id, dbFallback, duration);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    /*释放锁*/
                    unlock(lockKey);
                }
            });
        }
        /*锁未获取成功*/
        /*返回过期的商铺信息*/
        return data;
    }


    /**
     * Description: tryLock 编写代码基于Redis的SETNX指令来实现互斥锁
     *
     * @return boolean
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, LOCK_SHOP_VALUE, Duration.ofSeconds(LOCK_SHOP_TTL));
        return BooleanUtils.isTrue(flag);

    }

    /**
     * Description: unlock 释放互斥锁
     *
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/8
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
