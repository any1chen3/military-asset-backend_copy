package com.military.asset.entity;

import lombok.Data;

@Data
public class Province {
    private String code;       // 省份ID（char(36)）
    private String name;       // 省份名称（如“江苏省”）
    private Integer level;     // 行政等级（如1）
    private String abbr;       // 省份简称（如“苏”）
    private String alias;      // 省份代号（char(1)，如“J”）
}