package com.military.asset.service.impl;

import com.military.asset.entity.CyberAsset;
import com.military.asset.entity.CyberAssetUsageAggregation;
import com.military.asset.mapper.CyberAssetMapper;
import com.military.asset.mapper.ReportUnitMapper;
import com.military.asset.service.CyberAssetUsageAnalysisService;
import com.military.asset.utils.CategoryMapUtils;
import com.military.asset.utils.CyberAssetUsageFormulaUtils;
import com.military.asset.utils.SoftwareAssetAgingCalculator;
import com.military.asset.vo.CyberAssetCategoryUsageVO;
import com.military.asset.vo.CyberAssetUsageInsightVO;
import com.military.asset.vo.CyberAssetUsageProvinceStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认实现：基于 cyber_asset 与 report_unit 表构建使用率分析数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CyberAssetUsageAnalysisServiceImpl implements CyberAssetUsageAnalysisService {

    private final CyberAssetMapper cyberAssetMapper;
    private final ReportUnitMapper reportUnitMapper;

    private static final List<String> CYBER_ASSET_CATEGORIES;

    static {
        Map<String, String> categoryMap = CategoryMapUtils.initCyberCategoryMap();
        CYBER_ASSET_CATEGORIES = Collections.unmodifiableList(
                categoryMap.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .distinct()
                        .collect(Collectors.toList())
        );
    }

    @Override
    public CyberAssetUsageInsightVO analyzeUsage(String reportUnit) {
        if (!StringUtils.hasText(reportUnit)) {
            throw new IllegalArgumentException("上报单位不能为空");
        }
        String province = reportUnitMapper.selectProvinceByReportUnit(reportUnit);
        if (!StringUtils.hasText(province)) {
            throw new IllegalArgumentException("未找到上报单位[" + reportUnit + "]对应的省份信息");
        }

        List<CyberAsset> unitAssets = cyberAssetMapper.selectByReportUnit(reportUnit);
        Map<String, List<CyberAsset>> assetsByCategory = unitAssets.stream()
                .filter(asset -> StringUtils.hasText(asset.getAssetCategory()))
                .collect(Collectors.groupingBy(CyberAsset::getAssetCategory));

        List<CyberAssetUsageAggregation> provinceAggregations =
                cyberAssetMapper.aggregateProvinceUsageByAssetCategory(province);
        Map<String, List<BigDecimal>> provinceUsageRateMap = buildProvinceUsageRateMap(provinceAggregations);

        List<CyberAssetCategoryUsageVO> categoryResults = new ArrayList<>();
        for (String category : CYBER_ASSET_CATEGORIES) {
            List<CyberAsset> assets = assetsByCategory.getOrDefault(category, Collections.emptyList());
            List<BigDecimal> provinceRates = provinceUsageRateMap.getOrDefault(category, Collections.emptyList());
            CyberAssetCategoryUsageVO categoryUsage = buildCategoryUsage(category, assets, provinceRates);
            if (categoryUsage != null) {
                categoryResults.add(categoryUsage);
            }
        }

        CyberAssetUsageInsightVO insightVO = new CyberAssetUsageInsightVO();
        insightVO.setReportUnit(reportUnit);
        insightVO.setProvince(province);
        insightVO.setCategories(categoryResults);
        insightVO.setAgingRate(calculateAgingRate(unitAssets));
        return insightVO;
    }

    private BigDecimal calculateAgingRate(List<CyberAsset> unitAssets) {
        if (CollectionUtils.isEmpty(unitAssets)) {
            return CyberAssetUsageFormulaUtils.calculateUsageRate(0, 0);
        }
        LocalDate agingThreshold = LocalDate.now().minusYears(8);
        int totalQuantity = 0;
        int agingQuantity = 0;
        for (CyberAsset asset : unitAssets) {
            if (asset == null) {
                continue;
            }
            int actualQuantity = safeQuantity(asset.getActualQuantity());
            totalQuantity += actualQuantity;
            LocalDate putIntoUseDate = asset.getPutIntoUseDate();
            if (putIntoUseDate != null && !putIntoUseDate.isAfter(agingThreshold)) {
                agingQuantity += actualQuantity;
            }
        }
        return CyberAssetUsageFormulaUtils.calculateUsageRate(agingQuantity, totalQuantity);
    }

    private CyberAssetCategoryUsageVO buildCategoryUsage(String category,
                                                         List<CyberAsset> assets,
                                                         List<BigDecimal> provinceRates) {
        CyberAssetCategoryUsageVO vo = new CyberAssetCategoryUsageVO();
        vo.setAssetCategory(category);

        int actualTotal = assets.stream()
                .map(CyberAsset::getActualQuantity)
                .filter(this::isPositive)
                .mapToInt(Integer::intValue)
                .sum();
        int usedTotal = assets.stream()
                .map(CyberAsset::getUsedQuantity)
                .filter(this::isPositive)
                .mapToInt(Integer::intValue)
                .sum();
        if (actualTotal == 0 && usedTotal == 0) {
            return null;
        }

        int agingTotal = assets.stream()
                .filter(asset -> SoftwareAssetAgingCalculator.requiresUpgrade(
                        asset.getPutIntoUseDate(), LocalDate.now()))
                .map(CyberAsset::getUsedQuantity)
                .map(this::safeQuantity)
                .mapToInt(Integer::intValue)
                .sum();

        vo.setActualQuantity(actualTotal);
        vo.setUsedQuantity(usedTotal);
        vo.setUnit(resolveUnit(assets));
        BigDecimal usageRate = CyberAssetUsageFormulaUtils.calculateUsageRate(usedTotal, actualTotal);
        vo.setUsageRate(usageRate);
        vo.setAgingQuantity(agingTotal);
        vo.setAgingRate(SoftwareAssetAgingCalculator.calculateAgingRatio(agingTotal, actualTotal));

        List<CyberAssetUsageFormulaUtils.UsageDurationSample> samples = buildUsageSamples(assets);
        BigDecimal usageYears = CyberAssetUsageFormulaUtils.calculateWeightedUsageYears(samples, LocalDate.now());
        if (usageYears.scale() > 2) {
            usageYears = usageYears.setScale(2, RoundingMode.HALF_UP);
        }
        vo.setUsageYears(usageYears);
        vo.setReplacementAdvice(CyberAssetUsageFormulaUtils.determineReplacementAdvice(usageYears));
        vo.setProvinceStats(buildProvinceStats(provinceRates));
        return vo;
    }

    private Map<String, List<BigDecimal>> buildProvinceUsageRateMap(List<CyberAssetUsageAggregation> aggregations) {
        Map<String, List<BigDecimal>> result = new HashMap<>();
        if (CollectionUtils.isEmpty(aggregations)) {
            return result;
        }
        for (CyberAssetUsageAggregation aggregation : aggregations) {
            if (aggregation == null || !StringUtils.hasText(aggregation.getAssetCategory())) {
                continue;
            }
            int used = safeQuantity(aggregation.getUsedQuantity());
            int actual = safeQuantity(aggregation.getActualQuantity());
            BigDecimal rate = CyberAssetUsageFormulaUtils.calculateUsageRate(used, actual);
            result.computeIfAbsent(aggregation.getAssetCategory(), key -> new ArrayList<>()).add(rate);
        }
        return result;
    }

    private List<CyberAssetUsageFormulaUtils.UsageDurationSample> buildUsageSamples(List<CyberAsset> assets) {
        if (CollectionUtils.isEmpty(assets)) {
            return CyberAssetUsageFormulaUtils.emptySamples();
        }
        return assets.stream()
                .filter(asset -> asset.getPutIntoUseDate() != null)
                .map(asset -> CyberAssetUsageFormulaUtils.buildSample(
                        asset.getPutIntoUseDate(),
                        safeQuantity(asset.getActualQuantity())))
                .collect(Collectors.toList());
    }

    private CyberAssetUsageProvinceStatsVO buildProvinceStats(List<BigDecimal> provinceRates) {
        List<BigDecimal> safeRates = provinceRates == null ? Collections.emptyList() : provinceRates;
        CyberAssetUsageProvinceStatsVO statsVO = new CyberAssetUsageProvinceStatsVO();
        statsVO.setAverage(CyberAssetUsageFormulaUtils.calculateMean(safeRates));
        statsVO.setMedian(CyberAssetUsageFormulaUtils.calculateMedian(safeRates));
        statsVO.setVariance(CyberAssetUsageFormulaUtils.calculateVariance(safeRates));
        return statsVO;
    }

    private String resolveUnit(List<CyberAsset> assets) {
        return assets.stream()
                .map(CyberAsset::getUnit)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private int safeQuantity(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}