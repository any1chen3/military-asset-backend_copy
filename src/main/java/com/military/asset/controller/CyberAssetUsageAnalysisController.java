package com.military.asset.controller;

import com.military.asset.service.CyberAssetUsageAnalysisService;
import com.military.asset.vo.CyberAssetUsageInsightVO;
import com.military.asset.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网信资产使用率分析接口。
 */
@RestController
@RequestMapping("/api/asset/cyber/usage-rate")
@RequiredArgsConstructor
@Slf4j
public class CyberAssetUsageAnalysisController {

    private final CyberAssetUsageAnalysisService analysisService;

    @GetMapping("/report-unit/{reportUnit}")
    public ResultVO<CyberAssetUsageInsightVO> analyze(@PathVariable String reportUnit) {
        try {
            CyberAssetUsageInsightVO insightVO = analysisService.analyzeUsage(reportUnit);
            return ResultVO.success(insightVO, "网信基础资产使用率分析成功");
        } catch (Exception ex) {
            log.error("网信基础资产使用率分析失败, reportUnit={}", reportUnit, ex);
            return ResultVO.fail("网信基础资产使用率分析失败：" + ex.getMessage());
        }
    }
}
