package com.myhd.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Description: JSONUtil
 * <br></br>
 * className: JSONUtil
 * <br></br>
 * packageName: com.myhd.utils
 *
 * @author jinhui-huang
 * @version 1.0
 * @email 2634692718@qq.com
 * @Date: 2023/10/7 16:25
 */
@Component
public class JSONUtil {
    private static ObjectMapper objectMapper;

    @Autowired
    private ObjectMapper initObjectMapper;

    @PostConstruct
    public void initObjectMapper() {
        JSONUtil.objectMapper = this.initObjectMapper;
    }


    private JSONUtil(){}

    public static <T> T toBean(String json, Class<T> clazz){
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJsonStr(Object object) {
        try {
            if (object instanceof String && object.equals("")) {
                return "";
            }
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> toList(String listJson){
        try {
            return objectMapper.readValue(listJson, ArrayList.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
