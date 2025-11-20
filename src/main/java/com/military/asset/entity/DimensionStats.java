package com.military.asset.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

// 维度统计对象（指标基于记录数）
@Data
public class DimensionStats {
    private Map<String, Integer> total = new HashMap<>();       // 某类别记录总数
    private Map<String, BigDecimal> average = new HashMap<>();  // 各分类记录数的均值
    private Map<String, Integer> median = new HashMap<>();      // 各分类记录数的中位数
    private Map<String, BigDecimal> variance = new HashMap<>(); // 各分类记录数的方差
}