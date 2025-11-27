package com.military.asset.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
public class DomesticRateData {
    private Map<String, BigDecimal> unitRate = new HashMap<>();
    private Map<String, BigDecimal> provinceRate = new HashMap<>();
    private Map<String, Integer> unitDomesticCount = new HashMap<>();
    private Map<String, DomesticCountStats> provinceDomesticCountStats = new HashMap<>();
}
