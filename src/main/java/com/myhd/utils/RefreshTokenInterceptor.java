package com.myhd.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.myhd.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;

import static com.myhd.utils.RedisConstants.LOGIN_USER_KEY;
import static com.myhd.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Description: LoginInterceptor
 * <br></br>
 * className: LoginInterceptor
 * <br></br>
 * packageName: com.myhd.utils
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/6 18:04
 */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Description: preHandle 前置拦截器
     * @return boolean
     * @author jinhui-huang
     * @Date 2023/10/6
     * */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*TODO 1. 获取header中的token*/
        Object token = request.getHeader("authorization");
        if (StrUtil.isBlankIfStr(token)) {
            return true;
        }
        /*TODO 2. 基于token获取redis中的用户*/
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        /*TODO 3. 判断用户是否存在*/
        if (userMap.isEmpty()) {
            return true;
        }
        /*TODO 5. 存在, 保存用户信息到ThreadLocal*/
        UserHolder.saveUser(BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false));
        /*TODO 6. 刷新token有效期*/
        stringRedisTemplate.expire(key, Duration.ofMinutes(LOGIN_USER_TTL));
        return true;
    }

    /**
     * Description: afterCompletion 请求之后
     * @return void
     * @author jinhui-huang
     * @Date 2023/10/6
     * */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
