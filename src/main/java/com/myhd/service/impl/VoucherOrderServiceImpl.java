package com.myhd.service.impl;

import com.myhd.dto.Result;
import com.myhd.dto.UserDTO;
import com.myhd.entity.SeckillVoucher;
import com.myhd.entity.VoucherOrder;
import com.myhd.mapper.VoucherOrderMapper;
import com.myhd.service.ISeckillVoucherService;
import com.myhd.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myhd.utils.RedisIdWorker;
import com.myhd.utils.UserHolder;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import static com.myhd.utils.RedisConstants.LOCK_ORDER_KEY;
import static com.myhd.utils.RedisConstants.SECKILL_VOUCHER_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**线程池*/
    @Resource
    private ExecutorService executorService;

    /**阻塞队列*/
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    /**代理对象*/
    private IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    /*初始化Lua脚本*/
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**执行任务的成员内部类*/
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    /*1. 获取队列中的订单信息*/
                    VoucherOrder voucherOrder = orderTasks.take();
                    /*2. 处理订单*/
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }

            }
        }
    }

    /**
     * Description: handleVoucherOrder 处理订单的逻辑业务
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/15
     * */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        /*获取用户id*/
        Long userId = voucherOrder.getUserId();
        /*一个用户一把锁, 保证所对象只针对这个用户, 所对象对当前用户唯一*/
        /*创建锁对象*/
        /*使用RedissonClient提供的可重入锁机制解决锁提前释放问题, 以及不可重入问题*/
        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
        /*获取锁*/
        /*失败不等待, 默认参数为: 锁释放时间为30s*/
        boolean isLock = lock.tryLock();
        /*判断是否获取锁成功*/
        if (!isLock) {
            /*获取锁失败, 返回错误信息或重试*/
            log.error("不允许重复判断");
        }
        /*获取锁成功*/
        /*获取锁之后发生了阻塞会产生误删锁的情况*/
        try {
            /*获取事务的代理对象*/
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            /*在释放所之前, 获取所版本号发生了阻塞也会产生锁误删的情况*/
            lock.unlock();
        }

    }

    /**
     * Description: init 在Spring初始化完后执行的方法
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/15
     * */
    @PostConstruct
    private void init() {
        executorService.submit(new VoucherOrderHandler());
    }

    /**
     * Description: scekillVoucher 秒杀业务, 不依赖于Lua脚本
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/15
     * */
    @Override
    public Result scekillVoucher(Long voucherId) {
        /*1. 查询优惠卷*/
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        /*2. 判断秒杀是否开始*/
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            /*尚未开始秒杀*/
            return Result.fail("秒杀尚未开始!");
        }
        /*3. 判断秒杀是否已经结束*/
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            /*尚未开始秒杀*/
            return Result.fail("秒杀已经结束!");
        }
        /*4. 判断库存是否充足*/
        if (seckillVoucher.getStock() < 1) {
            /*库存不足*/
            return Result.fail("库存不足");
        }
        /*TODO: 注意spring事务实在synchronized释放锁后再提交事务的, 但是在synchronized释放锁后
         *  有可能导致其他线程进来执行代码同时也执行sql语句了, 从而导致线程并发安全, 所以必须在事务提交之后再释放锁*/
        /*6.0. 一人一单*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        /*一个用户一把锁, 保证所对象只针对这个用户, 所对象对当前用户唯一*/
        /*创建锁对象*/
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        /*使用RedissonClient提供的可重入锁机制解决锁提前释放问题, 以及不可重入问题*/
        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
        /*获取锁*/
        /*失败不等待, 默认参数为: 锁释放时间为30s*/
        boolean isLock = lock.tryLock();
        /*判断是否获取锁成功*/
        if (!isLock) {
            /*获取锁失败, 返回错误信息或重试*/
            return Result.fail("不允许重复下单");
        }
        /*获取锁成功*/
        /*获取锁之后发生了阻塞会产生误删锁的情况*/
        try {

            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            /*在释放所之前, 获取所版本号发生了阻塞也会产生锁误删的情况*/
            lock.unlock();
        }

    }

    /**
     * Description: createVoucherOrder 封装创建订单的方法, 使用spring管理的事务
     *
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/10
     */
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId, Long userId) {
        /*一人一单*/
        /*6.1. 查询订单*/
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        /*模拟业务阻塞导致锁提前释放的一个锁并发安全问题, 会产生超卖问题*/
        /*try {
            Thread.sleep(5001);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }*/

        /*6.2. 判断是否存在*/
        if (count > 0) {
            /*用户已经购买过了*/
            return Result.fail("用户已经购买过了");
        }

        /*5. 扣减库存*/
        /*TODO 扣减前加个sql条件判断一下查询到stock的值是否大于0*/
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) /*where id = ? and stock > 0*/
                .update();

        if (!success) {
            /*扣减失败*/
            return Result.fail("库存不足!");
        }

        /*6. 创建订单*/
        VoucherOrder voucherOrder = new VoucherOrder();
        /*6.1 订单id*/
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        /*6.2 用户id*/
        voucherOrder.setUserId(userId);
        /*6.3 代金卷id*/
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        /*7. 返回订单id*/
        return Result.ok(orderId);

    }

    /*基于Lua脚本的秒杀业务*/
    @Override
    public Result scekillVoucherByLua(Long voucherId) {
        String key = SECKILL_VOUCHER_KEY + voucherId;
        /*1. 从Redis查询优惠卷的信息*/
        String beginTimeStr = (String) stringRedisTemplate.opsForHash().get(key, "beginTime");
        String endTimeStr = (String) stringRedisTemplate.opsForHash().get(key, "endTime");
        if (StringUtils.isAllBlank(beginTimeStr, endTimeStr)) {
            return Result.fail("没有该优惠卷");
        }
        assert beginTimeStr != null;
        LocalDateTime beginTime = LocalDateTime.parse(beginTimeStr);
        assert endTimeStr != null;
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

        /*2. 判断秒杀是否开始*/
        if (beginTime.isAfter(LocalDateTime.now())) {
            /*尚未开始秒杀*/
            return Result.fail("秒杀尚未开始!");
        }
        /*3. 判断秒杀是否已经结束*/
        if (endTime.isBefore(LocalDateTime.now())) {
            /*尚未开始秒杀*/
            return Result.fail("秒杀已经结束!");
        }
        /*获取用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        /*1. 执行lua脚本*/
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, /*脚本文件*/
                Collections.emptyList(), /*空集合*/
                voucherId.toString(), /*优惠卷id*/
                userId.toString() /*用户id*/
        );
        /*2. 判断结果是否为0*/
        assert result != null;
        int r = result.intValue();
        if (r != 0) {
            /*2.1. 不为0, 代表没有购买资格*/
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        /*2.2. 为0, 有购买资格, 把下单信息保存到阻塞队列*/
        /*TODO 保存阻塞队列*/
        /*6. 创建订单*/
        VoucherOrder voucherOrder = new VoucherOrder();
        /*6.1 订单id*/
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        /*6.2 用户id*/
        voucherOrder.setUserId(userId);
        /*6.3 代金卷id*/
        voucherOrder.setVoucherId(voucherId);
        /*TODO 放入阻塞队列*/
        orderTasks.add(voucherOrder);

        /*获取事务的代理对象*/
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        /*3. 返回订单id*/
        return Result.ok(orderId);
    }

    /**
     * Description: createVoucherOrder 重载创建订单的方法
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/15
     * */
    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        /*1. 一人一单*/
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        /*2. 查询订单*/
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        /*6.2. 判断是否存在*/
        if (count > 0) {
            /*用户已经购买过了*/
            log.error("用户已经购买过了");
        }

        /*5. 扣减库存*/
        /*TODO 扣减前加个sql条件判断一下查询到stock的值是否大于0*/
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) /*where id = ? and stock > 0*/
                .update();

        if (!success) {
            /*扣减失败*/
            log.error("库存不足!");
        }

        /*保存订单*/
        save(voucherOrder);
    }
}
