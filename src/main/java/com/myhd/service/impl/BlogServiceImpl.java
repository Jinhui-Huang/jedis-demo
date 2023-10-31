package com.myhd.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myhd.dto.Result;
import com.myhd.dto.ScrollResult;
import com.myhd.dto.UserDTO;
import com.myhd.entity.Blog;
import com.myhd.entity.Follow;
import com.myhd.entity.User;
import com.myhd.mapper.BlogMapper;
import com.myhd.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myhd.service.IFollowService;
import com.myhd.service.IUserService;
import com.myhd.utils.RedisConstants;
import com.myhd.utils.SystemConstants;
import com.myhd.utils.UserHolder;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.myhd.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.myhd.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        /*1. 查询blog*/
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        /*2. 查询blog有关的用户*/
        this.queryBlogUser(blog);
        /*3. 查询blg是否被点赞*/
        this.isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        /*1. 获取登录用户*/
        UserDTO user = UserHolder.getUser(UserDTO.class);
        if (user != null) {
            Long userId = user.getId();
            /*2. 判断当前登录用户是否已经点赞*/
            String key = BLOG_LIKED_KEY + blog.getId();
            Boolean isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
            blog.setIsLike(BooleanUtils.isTrue(isMember));
        }

    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        /*1. 获取登录用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        /*2. 判断当前登录用户是否已经点赞, 为null则可以点赞*/
        String key = BLOG_LIKED_KEY + id;
        Boolean isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
        /*判断是否点赞*/
        if (!BooleanUtils.isTrue(isMember)) {
            /*3. 如果未点赞, 可以点赞*/
            /*3.1. 数据库点赞数 + 1*/
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            /*3.2. 保存用户到Redis的SortedSet集合中去*/
            if (isSuccess) {
                /*zadd key value score*/
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            /*4. 如果已点赞, 取消点赞*/
            /*4.1. 数据库点赞数 -1*/
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            /*4.2. 把用户从Redis的SortedSet集合移除*/
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        /*1. 查询top5的点赞用户 zrange key 0 4*/
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        /*2. 解析用户id*/
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        /*3. 根据用户id查询用户 WHERE id IN(5, 1) ORDER BY FIELD(id, 5, 1)*/
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        /*4. 返回*/
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        /*1. 获取登录用户*/
        UserDTO user = UserHolder.getUser(UserDTO.class);
        blog.setUserId(user.getId());
        /*2. 保存探店博文*/
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败!");
        }
        /*3. 查询笔记作者的所有粉丝*/
        /*select * from tb_follow where follow_user_id = ?*/
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        /*4. 推送笔记id给所有粉丝*/
        for (Follow follow : follows) {
            /*4.1. 获取粉丝id*/
            Long userId = follow.getUserId();
            /*4.2. 推送到Redis的SortedSet*/
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        /*5. 返回id*/
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        /*1. 获取当前用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        /*2. 查询收件箱*/
        String key = FEED_KEY + userId;
        /*ZREVRANGEBYSCORE key max min WITHSCORES LIMIT offset count*/
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        /*3.0. 非空判断*/
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        /*3. 解析数据: blogId, minTime(时间戳), offset*/
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        /*偏移量offset*/
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            /*3.1. 获取id*/
            ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            /*3.2. 获取分数(时间戳)*/
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();
            /*3.3. 统计偏移量offset*/
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        /*4. 根据id查询blog*/
        /*4.1. 需要考虑顺序性, 和blog是否被点赞*/
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();
        /*4.2. blog是否被当前用户点赞*/
        for (Blog blog : blogs) {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        }
        /*5. 封装并返回*/
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);

        return Result.ok(scrollResult);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
