package com.military.asset.controller;

import com.military.asset.service.SoftwareAssetStatisticsService;
import com.military.asset.vo.ResultVO;
import com.military.asset.vo.stat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * 软件资产统计控制器，提供符合前端展示格式的聚合数据。
 */
@RestController
@RequestMapping({"/api/asset/software/statistics", "/api/asset/software/statistics/v2"})
@RequiredArgsConstructor
@Slf4j
public class SoftwareAssetStatisticsController {

    private final SoftwareAssetStatisticsService statisticsService;

    @GetMapping("/acquisition")
    public ResultVO<List<SoftwareAssetAcquisitionStatisticVO>> acquisitionStatistics() {
        return executeList(() -> statisticsService.listAcquisitionStatistics(), "软件资产取得方式统计查询成功");
    }

    @GetMapping("/service-status")
    public ResultVO<List<SoftwareAssetServiceStatusStatisticVO>> serviceStatusStatistics() {
        return executeList(() -> statisticsService.listServiceStatusStatistics(), "软件资产服务状态统计查询成功");
    }

    @GetMapping("/aging/province")
    public ResultVO<List<SoftwareAssetAgingStatisticVO>> provinceAgingStatistics() {
        return executeList(() -> statisticsService.listProvinceAgingStatistics(),
                "软件资产省份老化统计查询成功");
    }

    @GetMapping("/aging/asset/{assetId}/upgrade-required")
    public ResultVO<SoftwareAssetUpgradeStatusVO> assetUpgradeRequired(@PathVariable("assetId") String assetId) {
        return execute(() -> statisticsService.determineAssetUpgradeStatus(assetId),
                "软件资产升级判定查询成功");
    }
    @GetMapping("/report-unit/{reportUnit}/insight")
    public ResultVO<SoftwareAssetInsightVO> reportUnitInsight(@PathVariable("reportUnit") String reportUnit) {
        return execute(() -> statisticsService.buildReportUnitInsight(reportUnit),
                "软件资产自主研发能力与服务状态综合统计查询成功");
    }

    private <T> ResultVO<List<T>> executeList(Supplier<List<T>> supplier, String successMessage) {
        try {
            List<T> statistics = supplier.get();
            return ResultVO.success(statistics, successMessage);
        } catch (Exception ex) {
            log.error("查询软件资产统计失败", ex);
            return ResultVO.fail("软件资产统计失败：" + ex.getMessage());
        }
    }

    private <T> ResultVO<T> execute(Supplier<T> supplier, String successMessage) {
        try {
            T result = supplier.get();
            return ResultVO.success(result, successMessage);
        } catch (Exception ex) {
            log.error("查询软件资产统计失败", ex);
            return ResultVO.fail("软件资产统计失败：" + ex.getMessage());
        }
    }
}