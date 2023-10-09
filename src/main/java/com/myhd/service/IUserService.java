package com.myhd.service;

import com.myhd.dto.LoginFormDTO;
import com.myhd.dto.Result;
import com.myhd.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Jinhui-Huang
 * @since 2023-10-05
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
