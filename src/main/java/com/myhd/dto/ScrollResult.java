package com.myhd.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    /**查询的集合*/
    private List<?> list;
    /**上次查询的最小id*/
    private Long minTime;
    /**偏移量*/
    private Integer offset;
}
