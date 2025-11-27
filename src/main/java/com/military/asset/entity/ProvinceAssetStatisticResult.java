package com.military.asset.entity;

import lombok.Data;

/**
 * 省份维度的数据内容资产统计结果。
 */
@Data
public class ProvinceAssetStatisticResult {

    private int code = 200;

    private String message = "查找成功";

    private ProvinceAssetStatisticData data = new ProvinceAssetStatisticData();
}
