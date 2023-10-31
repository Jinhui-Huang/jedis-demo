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
import com.myhd.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.myhd.utils.RedisConstants.*;
import static com.myhd.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Description: sendCode 发送手机验证码
     *
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/6
     */
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
     *
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/6
     */
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
        if (!cacheCode.equals(code)) {
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
     * Description: sign 用户签到功能
     *
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/31
     */
    @Override
    public Result sign() {
        /*1. 获取当前登录用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        /*2. 获取日期*/
        LocalDateTime now = LocalDateTime.now();
        /*3. 拼接key*/
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        /*4. 获取今天是本月第几天*/
        int dayOfMonth = now.getDayOfMonth();
        /*5. 写入Redis, SETBIT key offset 1, true表示已签到*/
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * Description: signCount 统计连续签到的次数
     *
     * @return com.myhd.dto.Result
     * @author jinhui-huang
     * @Date 2023/10/31
     */
    @Override
    public Result signCount() {
        /*1. 获取本月截止今天为止的所有的签到记录*/
        /*1. 获取当前登录用户*/
        Long userId = UserHolder.getUser(UserDTO.class).getId();
        /*2. 获取日期*/
        LocalDateTime now = LocalDateTime.now();
        /*3. 拼接key*/
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        /*4. 获取今天是本月第几天*/
        int dayOfMonth = now.getDayOfMonth();
        /*5. 获取本月截止今天为止的所有的签到记录, 返回的是一个十进制的数字*/
        /*BITFIELD sign:1010:202310 GET u31 0*/
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            /*没有任何结果*/
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        /*6. 循环遍历*/
        int count = 0;
        while (true) {
            /*7. 让这个数字与1做与运算, 得到数字的最后一个bit位*/
            /*8. 判断这个bit位是否为0*/
            if ((num & 1) == 0) {
                /*如果为0, 说明未签到, 结束*/
                break;
            } else {
                /*如果不为0, 说明已签到, 计数器+1*/
                count++;
            }
            /*把数字右移一位, 抛弃这一位, 继续判断下一位, 无符号右移*/
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * Description: createUserWithPhone 创建新用户
     *
     * @return com.myhd.entity.User
     * @author jinhui-huang
     * @Date 2023/10/6
     */
    private User createUserWithPhone(String phone) {
        /*1. 创建用户*/
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        user.setIcon("");
        return user;
    }
}
