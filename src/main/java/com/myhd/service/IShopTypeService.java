package com.myhd.service;

import com.myhd.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
public interface IShopTypeService extends IService<ShopType> {

    List<ShopType> selectShopTypes();
}
