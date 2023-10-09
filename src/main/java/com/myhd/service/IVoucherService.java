package com.myhd.service;

import com.myhd.dto.Result;
import com.myhd.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
public interface IVoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

}
