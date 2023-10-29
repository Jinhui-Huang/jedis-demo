package com.myhd.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myhd.dto.Result;
import com.myhd.dto.UserDTO;
import com.myhd.entity.Follow;
import com.myhd.entity.User;
import com.myhd.mapper.FollowMapper;
import com.myhd.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myhd.service.IUserService;
import com.myhd.utils.RedisConstants;
import com.myhd.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.myhd.utils.RedisConstants.FOLLOWS_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * Description: follow 关注和取关功能
     *
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/29
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        /*0. 获取登录用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        String key = FOLLOWS_KEY + userId;
        /*1. 判断到底是关注还是取关*/
        if (isFollow) {
            /*2. 关注, 新增数据*/
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                /*保存成功同时把关注用户的id, 存入Redis中去*/
                /*sadd userID followerUserId*/
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            /*3. 取关, 删除 delete from, tb_follow where userId = ? and follow_use_id = ?*/
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                /*把关注的用户id从redis从集合中移除*/
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * Description: isFollow 判断是否关注
     *
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/29
     */
    @Override
    public Result isFollow(Long followUserId) {
        /*0. 获取登录用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        /*1. 查询是否关注 select count(*) from, tb_follow where userId = ? and follow_use_id = ?*/
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        /*判断count > 0 则关注*/
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        /*1. 获取当前登录用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        String userKey = FOLLOWS_KEY + userId;
        /*2. 求交集*/
        String followKey = FOLLOWS_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, followKey);
        /*3. 解析id集合*/
        if (intersect == null || intersect.isEmpty()) {
            /*无交集*/
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        /*4. 查询用户*/
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        /*返回用户*/
        return Result.ok(users);

    }
}
