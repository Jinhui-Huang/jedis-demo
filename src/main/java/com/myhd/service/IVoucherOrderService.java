package com.myhd.service;

import com.myhd.dto.Result;
import com.myhd.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result scekillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId, Long userId);
}
