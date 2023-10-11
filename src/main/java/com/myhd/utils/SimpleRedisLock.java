package com.myhd.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Description: SimpleRedisLock
 * <br></br>
 * className: SimpleRedisLock
 * <br></br>
 * packageName: com.myhd.utils
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/10 19:51
 */
public class SimpleRedisLock implements ILock{

    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID() + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    /*初始化Lua脚本*/
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**业务名称 + 用户id*/
    private String name;

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Description: tryLock 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间, 过期后自动释放锁
     * @return boolean true代表获取锁成功, false代表获取锁失败
     * @author jinhui-huang
     * @Date 2023/10/10
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        /*获取线程标识*/
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        /*获取锁*/
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, Duration.ofSeconds(timeoutSec));
        return Boolean.TRUE.equals(success);
    }

    /**
     * Description: unlock 释放锁
     *
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/10
     */
    @Override
    public void unlockNoLua() {
        /*获取线程标示*/
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            /*释放锁*/
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }

    @Override
    public void unlock() {
        /*调用lua脚本*/
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
}
