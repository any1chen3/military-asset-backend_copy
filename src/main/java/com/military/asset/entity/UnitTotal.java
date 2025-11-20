package com.military.asset.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

// 上报单位自身总量
@Data
public class UnitTotal {
    private Map<String, Integer> applicationFieldTotal = new HashMap<>();
    private Map<String, Integer> developmentToolTotal = new HashMap<>();
    private Map<String, Integer> updateMethodTotal = new HashMap<>();
}
