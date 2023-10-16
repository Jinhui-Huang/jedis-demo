package com.myhd.service.impl;

import com.myhd.dto.Result;
import com.myhd.entity.SeckillVoucher;
import com.myhd.entity.Voucher;
import com.myhd.mapper.VoucherMapper;
import com.myhd.service.ISeckillVoucherService;
import com.myhd.service.IVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myhd.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.myhd.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.myhd.utils.RedisConstants.SECKILL_VOUCHER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        /*保存秒杀库存到Redis中*/
        String key = SECKILL_VOUCHER_KEY + voucher.getId();

        stringRedisTemplate.opsForHash().put(key, "stock", voucher.getStock().toString());
        stringRedisTemplate.opsForHash().put(key, "beginTime", voucher.getBeginTime().toString());
        stringRedisTemplate.opsForHash().put(key, "endTime", voucher.getEndTime().toString());
    }
}
