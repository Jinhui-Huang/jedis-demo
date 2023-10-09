package com.myhd.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import com.myhd.dto.LoginFormDTO;
import com.myhd.dto.Result;
import com.myhd.dto.UserDTO;
import com.myhd.entity.User;
import com.myhd.mapper.UserMapper;
import com.myhd.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myhd.utils.AliSms;
import com.myhd.utils.RedisConstants;
import com.myhd.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.myhd.utils.RedisConstants.*;
import static com.myhd.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Description: sendCode 发送手机验证码
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/6
     * */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        /*1. 校验手机号*/
        if (RegexUtils.isPhoneInvalid(phone)) {
            /*2. 如果不符合, 返回错误信息*/
            return Result.fail("手机号格式错误");
        }
        /*3. 符合, 生成验证码*/
        String code = RandomUtil.randomNumbers(6);
        log.info("发送验证码成功, 验证码: {}", code);

        /*4. 保存验证码到Redis, 5分钟后过期*/
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, Duration.ofMinutes(LOGIN_CODE_TTL));

        /*5. 发送验证码*/
        try {
            AliSms.sendPhoneCode(phone, code, true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail("服务忙请稍后再试");
        }
        /*6. 返回ok*/
        return Result.ok();
    }

    /**
     * Description: login 校验手机验证码
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/6
     * */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        /*1. 校验手机号*/
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            /* 如果不符合, 返回错误信息*/
            return Result.fail("手机号格式错误");
        }
        /*2. TODO 从Redis获取验证码来校验验证码*/
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            /*3. 不一致, 报错*/
            return Result.fail("登录的手机号前后不一致");
        }

        String code = loginForm.getCode();
        if (!cacheCode.equals(code) ) {
            /*3. 不一致, 报错*/
            return Result.fail("验证码错误");
        }

        /*4. 一致, 根据手机号查询用户*/
        User user = query().eq("phone", phone).one();
        /*5. 判断用户是否存在*/
        if (user == null) {
            /*6. 不存在, 创建新用户并保存*/
            user = createUserWithPhone(phone);
        }
        /*7. TODO 存在, 保存用户信息到Redis中*/
        /*7.1. TODO 随机生成token, 作为登录令牌*/
        String token = UUID.randomUUID(true).toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        /*7.2. TODO 将User对象转为Hash存储*/
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((filedName, filedValue) -> filedValue.toString())));
        /*7.3. 30分钟后过期*/
        stringRedisTemplate.expire(tokenKey, Duration.ofMinutes(LOGIN_USER_TTL));

        /*8. TODO 返回token*/
        return Result.ok(token);
    }

    /**
     * Description: createUserWithPhone 创建新用户
     * @return com.myhd.entity.User
     * @author jinhui-huang
     * @Date 2023/10/6
     * */
    private User createUserWithPhone(String phone) {
        /*1. 创建用户*/
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }
}
