package com.military.asset.service;

import com.military.asset.vo.PhoneNumberAnalysisVO;

/**
 * 电话号码分类分析服务。
 */
public interface PhoneNumberAnalysisService {

    /**
     * 基于四类电话号码统计上报单位的工作性质。
     *
     * @param reportUnit 上报单位
     * @return 电话号码分析结果
     */
    PhoneNumberAnalysisVO analyze(String reportUnit);
}
