package com.military.asset.entity;

import lombok.Data;

@Data
public class DomesticRateResult {
    private int code = 200;
    private String message = "查找成功";
    private DomesticRateData data = new DomesticRateData();
}
