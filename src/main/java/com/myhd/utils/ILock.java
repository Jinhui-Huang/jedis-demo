package com.myhd.utils;

/**
 * Description: ILock
 * <br></br>
 * className: ILock
 * <br></br>
 * packageName: com.myhd.utils
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/10 19:07
 */
public interface ILock {

    /**
     * Description: tryLock 尝试获取锁
     * @return boolean true代表获取锁成功, false代表获取锁失败
     * @param timeoutSec 锁持有的超时时间, 过期后自动释放锁
     * @author jinhui-huang
     * @Date 2023/10/10
     * */
    boolean tryLock(long timeoutSec);

    /**
     * Description: unlock 释放锁
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/10
     * */
    void unlock();

}
