package com.myhd.service.impl;

import com.myhd.utils.JSONUtil;
import com.myhd.entity.ShopType;
import com.myhd.mapper.ShopTypeMapper;
import com.myhd.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

import static com.myhd.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> selectShopTypes() {
        String key = CACHE_SHOP_TYPE_KEY;
        Long size = stringRedisTemplate.opsForList().size(key);
        /*存在直接返回*/
        if (size !=null && size > 0) {
            return JSONUtil.toList(Objects.requireNonNull(stringRedisTemplate.opsForList().range(key, 0, size - 1)).toString());
        }

        /*不存在则查询*/
        List<ShopType> list = query().orderByAsc("sort").list();
        /*null返回空集合*/
        if (list == null || list.isEmpty()) {
            return list;
        }
        /*存在存入redis*/
        for (ShopType shopType : list) {
            stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(shopType));
        }
        return list;
    }
}
