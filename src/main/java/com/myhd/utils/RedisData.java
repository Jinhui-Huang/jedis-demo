package com.myhd.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    /**逻辑过期时间*/
    private LocalDateTime expireTime;

    /**存入Redis的数据*/
    private Object data;

    /**存入对应类型的数据*/
    public void setData(Object data) {
        this.data = data;
    }

    /**返回对应类型数据*/
    public <T> T getData(Class<T> clazz) {
        return JSONUtil.toBean(JSONUtil.toJsonStr(this.data), clazz);
    }
}
