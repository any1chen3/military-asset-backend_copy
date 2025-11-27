package com.military.asset.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 省份维度的数据内容资产统计数据。
 */
@Data
public class ProvinceAssetStatisticData {

    /**
     * 各应用领域的统计结果。
     * key: 应用领域名称
     * value: 该应用领域下的统计数据
     */
    private Map<String, ProvinceAssetStatisticItem> applicationFields = new HashMap<>();
}
