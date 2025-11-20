package com.military.asset.service;

import com.military.asset.vo.CyberAssetUsageInsightVO;

/**
 * 网信基础资产使用率分析服务。
 */
public interface CyberAssetUsageAnalysisService {

    /**
     * 根据上报单位统计各类网信资产的使用率及省内对比指标。
     *
     * @param reportUnit 上报单位
     * @return 使用率分析结果
     */
    CyberAssetUsageInsightVO analyzeUsage(String reportUnit);
}
