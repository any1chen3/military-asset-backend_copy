package com.military.asset.controller;

import com.military.asset.entity.ProvinceAssetStatisticResult;
import com.military.asset.entity.StatisticResult;
import com.military.asset.entity.DomesticRateResult;
import com.military.asset.service.impl.DataContentAssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-content")
public class DataContentAssetController {

    @Autowired
    private DataContentAssetService assetService;

    @GetMapping("/statistic")
    public StatisticResult getStatistic(@RequestParam String reportUnit) {
        return assetService.getStatistic(reportUnit);
    }

    @GetMapping("/province-statistic")
    public ProvinceAssetStatisticResult getProvinceStatistic(@RequestParam String reportUnit) {
        return assetService.getProvinceAssetStatistic(reportUnit);
    }

    @GetMapping("/domestic-rate")
    public DomesticRateResult getDomesticRate(@RequestParam String reportUnit) {
        return assetService.getDomesticRate(reportUnit);
    }
}