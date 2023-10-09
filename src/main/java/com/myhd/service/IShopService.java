package com.myhd.service;

import com.myhd.dto.Result;
import com.myhd.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id, String cacheDealWithType);

    Result update(Shop shop);

}
