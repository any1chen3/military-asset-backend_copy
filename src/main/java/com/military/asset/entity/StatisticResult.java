package com.military.asset.entity;

import lombok.Data;

@Data
public class StatisticResult {
    private int code = 200;
    private String message = "查找成功";
    private StatisticData data = new StatisticData();
}
