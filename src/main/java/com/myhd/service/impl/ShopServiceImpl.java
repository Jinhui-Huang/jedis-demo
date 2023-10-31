package com.myhd.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myhd.utils.CacheClient;
import com.myhd.dto.Result;
import com.myhd.entity.Shop;
import com.myhd.mapper.ShopMapper;
import com.myhd.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myhd.utils.RedisConstants;
import com.myhd.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.Duration;
import java.util.*;

import static com.myhd.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private Random random;

    /**
     * Description: queryById 查询店铺信息, 选择不同的缓存击穿解决方案,
     * <li>DEAL_CACHE_STAVE_BY_LOCK: 根据互斥锁解决缓存击穿问题</li>
     * <li>DEAL_CACHE_STAVE_BY_LOGIC: 根据逻辑过期解决缓存击穿问题</li>
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/8
     * */
    public Result queryById(Long id, String cacheDealWithType){
        if (cacheDealWithType.equals(DEAL_CACHE_STAVE_BY_LOCK)) {
            return queryByIdWithLock(id);
        } else if (cacheDealWithType.equals(DEAL_CACHE_STAVE_BY_LOGIC)) {
            return queryByIdWithLogic(id);
        } else {
            return Result.fail("系统异常!");
        }
    }

    /**
     * Description: queryById Redis缓存核心代码, 热点key的缓存穿透, 缓存雪崩, 缓存击穿等问题的解决方案, 这个方法采取了基于互斥锁的方案
     * 解决了缓存击穿问题
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/8
     * */
    private Result queryByIdWithLock(Long id) {
        /*解决缓存穿透*/
        /* TODO 缓存雪崩: 防止大量缓存同时失效导致缓存雪崩, 给保存时间再附加一个随机时间让存储时间在30~40*/
        Shop shop = cacheClient.queryWithPassThroughLock(
                        CACHE_SHOP_KEY,
                        LOCK_SHOP_KEY,
                        id,
                        Shop.class,
                        this::getById,
                        Duration.ofMinutes(CACHE_SHOP_TTL + random.nextInt(11)
                        ));
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        /*8. 返回对象*/
        return Result.ok(shop);
    }


    /**
     * Description: update 为了实现数据库与Redis的数据一致性, 采用了高一致性原则, 在数据库需要更新数据时, 先更新数据库,
     * 在删除Redis的缓存
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/8
     * */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        /*1. 更新数据库*/
        updateById(shop);
        /*2. 删除缓存*/
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    /**
     * Description: queryShopByType 按照条件分页查询店铺数据
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/31
     * */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        /*1. 判断是否需要根据坐标查询*/
        if (x == null || y == null) {
            /*不需要坐标查询, 按数据库分页查询*/
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        /*2. 计算分页参数*/
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        /*3. 查询redis, 按照距离排序, 分页. 结果: shopId, distance*/
        /*GEOSEARCH BYLONLAT x y BYRADIUS 10 WITHDISTANCE*/
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if (results == null) {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        /*4.0. 截取from ~ end的部分*/
        /*店铺id集合*/
        List<Long> ids = new ArrayList<>(list.size());
        /*店铺距离*/
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            /*4.1. 获取店铺id*/
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            /*4.2. 获取距离*/
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        /*5. 根据id查询shop*/
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        /*6. 返回*/
        return Result.ok(shops);
    }


    /**
     * Description: queryByIdWithLogic Redis缓存核心代码, 热点key的缓存穿透, 缓存雪崩, 缓存击穿等问题的解决方案, 这个方法采取了逻辑过期的方案, 需要在项目启动时把数据库里的热点key导入Redis中
     * 解决了缓存击穿问题
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/8
     * */
    private Result queryByIdWithLogic(Long id) {
        Shop shop = cacheClient
                .queryWithPassThroughLogic(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById, Duration.ofSeconds(CACHE_SHOP_TTL_SECONDS + random.nextInt(600)));
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

}
