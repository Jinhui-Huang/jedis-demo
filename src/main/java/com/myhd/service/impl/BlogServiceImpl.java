package com.myhd.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myhd.dto.Result;
import com.myhd.dto.UserDTO;
import com.myhd.entity.Blog;
import com.myhd.entity.User;
import com.myhd.mapper.BlogMapper;
import com.myhd.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myhd.service.IUserService;
import com.myhd.utils.SystemConstants;
import com.myhd.utils.UserHolder;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.myhd.utils.RedisConstants.BLOG_LIKED_KEY;

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
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        /*1. 查询blog*/
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        /*2. 查询blog有关的用户*/
        queryBlogUser(blog);
        /*3. 查询blg是否被店主*/
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        /*1. 获取登录用户*/
        UserDTO user = UserHolder.getUser(UserDTO.class);
        if (user != null) {
            Long userId = user.getId();
            /*2. 判断当前登录用户是否已经点赞*/
            String key = BLOG_LIKED_KEY + blog.getId();
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
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
        /*2. 判断当前登录用户是否已经点赞*/
        String key = BLOG_LIKED_KEY + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        /*判断是否点赞*/
        if (!BooleanUtils.isTrue(isMember)) {
            /*3. 如果未点赞, 可以点赞*/
            /*3.1. 数据库点赞数 + 1*/
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            /*3.2. 保存用户到Redis的set集合中去*/
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            }
        } else {
            /*4. 如果已点赞, 取消点赞*/
            /*4.1. 数据库点赞数 -1*/
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            /*4.2. 把用户从Redis的set集合移除*/
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
