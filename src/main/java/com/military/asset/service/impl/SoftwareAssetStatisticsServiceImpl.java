package com.military.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.military.asset.entity.Province;
import com.military.asset.entity.ReportUnit;
import com.military.asset.entity.SoftwareAsset;
import com.military.asset.mapper.ProvinceMapper;
import com.military.asset.mapper.ReportUnitMapper;
import com.military.asset.mapper.SoftwareAssetStatisticsMapper;
import com.military.asset.service.SoftwareAssetService;
import com.military.asset.service.SoftwareAssetStatisticsService;
import com.military.asset.utils.StatisticsCalculator;
import com.military.asset.utils.SoftwareAssetAgingCalculator;
import com.military.asset.vo.stat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 软件资产统计服务实现，封装聚合查询与占比计算逻辑。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SoftwareAssetStatisticsServiceImpl implements SoftwareAssetStatisticsService {

    private final SoftwareAssetStatisticsMapper statisticsMapper;
    private final ProvinceMapper provinceMapper;
    private final ReportUnitMapper reportUnitMapper;
    private final SoftwareAssetService softwareAssetService;

    @Override
    public List<SoftwareAssetAcquisitionStatisticVO> listAcquisitionStatistics() {
        List<SoftwareAssetStatisticRow> rows = fetchRows();
        if (rows.isEmpty()) {
            log.info("未查询到任何软件资产取得方式统计数据");
            return Collections.emptyList();
        }
        return rows.stream()
                .map(this::convertAcquisition)
                .collect(Collectors.toList());
    }

    @Override
    public List<SoftwareAssetServiceStatusStatisticVO> listServiceStatusStatistics() {
        List<SoftwareAssetStatisticRow> rows = fetchRows();
        if (rows.isEmpty()) {
            log.info("未查询到任何软件资产服务状态统计数据");
            return Collections.emptyList();
        }
        return rows.stream()
                .map(this::convertServiceStatus)
                .collect(Collectors.toList());
    }

    @Override
    public List<SoftwareAssetAgingStatisticVO> listProvinceAgingStatistics() {
        List<Province> provinces = provinceMapper.selectAll();
        Map<String, ProvinceAgingAccumulator> accumulatorMap = new LinkedHashMap<>();
        if (provinces != null) {
            for (Province province : provinces) {
                if (province == null) {
                    continue;
                }
                String name = province.getName();
                if (name != null && !name.isEmpty()) {
                    accumulatorMap.putIfAbsent(name, new ProvinceAgingAccumulator(name));
                }
            }
        }

        List<SoftwareAssetProvinceUsageDetail> details = fetchAllProvinceDetails();
        if (details.isEmpty()) {
            return accumulatorMap.values().stream()
                    .map(ProvinceAgingAccumulator::toStatistic)
                    .collect(Collectors.toList());
        }

        LocalDate today = LocalDate.now();
        for (SoftwareAssetProvinceUsageDetail detail : details) {
            if (detail == null) {
                continue;
            }
            int quantity = SoftwareAssetAgingCalculator.safeQuantity(detail.getActualQuantity());
            if (quantity <= 0) {
                continue;
            }
            boolean inUse = Objects.equals(IN_USE_STATUS, detail.getServiceStatus());
            boolean requiresUpgrade = SoftwareAssetAgingCalculator.requiresUpgrade(detail.getPutIntoUseDate(), today);
            String provinceName = detail.getProvince();
            if (provinceName != null) {
                provinceName = provinceName.trim();
            }
            ProvinceAgingAccumulator accumulator = resolveAccumulator(accumulatorMap, provinceName);
            accumulator.add(inUse, quantity, requiresUpgrade);
        }

        return accumulatorMap.values().stream()
                .map(ProvinceAgingAccumulator::toStatistic)
                .collect(Collectors.toList());
    }

    @Override
    public SoftwareAssetUpgradeStatusVO determineAssetUpgradeStatus(String assetId) {
        SoftwareAsset asset = softwareAssetService.getById(assetId);
        LocalDate today = LocalDate.now();
        SoftwareAssetUpgradeStatusVO vo = new SoftwareAssetUpgradeStatusVO();
        vo.setAssetId(asset.getId());
        vo.setAssetName(asset.getAssetName());
        vo.setServiceStatus(asset.getServiceStatus());
        vo.setPutIntoUseDate(asset.getPutIntoUseDate());
        vo.setReferenceDate(today);
        vo.setThresholdYears(SoftwareAssetAgingCalculator.DEFAULT_THRESHOLD_YEARS);
        vo.setRequiresUpgrade(SoftwareAssetAgingCalculator.requiresUpgrade(asset.getPutIntoUseDate(), today));
        return vo;
    }


    @Override
    public SoftwareAssetUpgradeOverviewVO listReportUnitUpgradeOverview(String reportUnit) {
        if (!StringUtils.hasText(reportUnit)) {
            throw new IllegalArgumentException("reportUnit不能为空");
        }

        String trimmedReportUnit = reportUnit.trim();
        LocalDate today = LocalDate.now();

        List<SoftwareAsset> assets = softwareAssetService.list(new QueryWrapper<SoftwareAsset>()
                .eq("report_unit", trimmedReportUnit)
                .orderByAsc("id"));

        List<SoftwareAssetUpgradeWithInfoVO> upgradeList = assets.stream()
                .map(asset -> {
                    SoftwareAssetUpgradeWithInfoVO vo = new SoftwareAssetUpgradeWithInfoVO();
                    vo.setAssetType(asset.getAssetCategory());
                    vo.setAssetName(asset.getAssetName());
                    vo.setAcquisitionMethod(asset.getAcquisitionMethod());
                    vo.setFunctionBrief(asset.getFunctionBrief());
                    vo.setDeploymentScope(asset.getDeploymentScope());
                    vo.setActualQuantity(asset.getActualQuantity());
                    vo.setUnit(asset.getUnit());
                    vo.setServiceStatus(asset.getServiceStatus());
                    vo.setPutIntoUseDate(asset.getPutIntoUseDate());
                    vo.setRequiresUpgrade(SoftwareAssetAgingCalculator.requiresUpgrade(asset.getPutIntoUseDate(), today));
                    return vo;
                })
                .collect(Collectors.toList());

        SoftwareAssetUpgradeOverviewVO overviewVO = new SoftwareAssetUpgradeOverviewVO();
        overviewVO.setReportUnit(trimmedReportUnit);
        overviewVO.setAssets(upgradeList);
        return overviewVO;
    }
    @Override
    public SoftwareAssetInsightVO buildReportUnitInsight(String reportUnit) {
        if (!StringUtils.hasText(reportUnit)) {
            throw new IllegalArgumentException("reportUnit不能为空");
        }

        String trimmedReportUnit = reportUnit.trim();
        String province = resolveProvince(trimmedReportUnit);
        Set<String> provinceUnits = resolveProvinceUnits(trimmedReportUnit, province);

        Map<String, SoftwareAssetStatisticRow> rowMap = new LinkedHashMap<>();
        SoftwareAssetStatisticRow targetRow = statisticsMapper.selectStatisticsByReportUnit(trimmedReportUnit);
        if (targetRow != null && StringUtils.hasText(targetRow.getReportUnit())) {
            rowMap.put(targetRow.getReportUnit(), targetRow);
        }

        List<SoftwareAssetStatisticRow> provinceRows = statisticsMapper.selectStatisticsByReportUnits(new ArrayList<>(provinceUnits));
        if (provinceRows != null) {
            for (SoftwareAssetStatisticRow row : provinceRows) {
                if (row == null || !StringUtils.hasText(row.getReportUnit())) {
                    continue;
                }
                rowMap.putIfAbsent(row.getReportUnit(), row);
            }
        }

        SoftwareAssetInsightVO insightVO = new SoftwareAssetInsightVO();
        insightVO.setReportUnit(trimmedReportUnit);
        insightVO.setProvince(province);
        SoftwareAssetDistributionSectionVO acquisitionSection = buildAcquisitionSection(trimmedReportUnit, rowMap, provinceUnits);
        SoftwareAssetDistributionSectionVO serviceStatusSection = buildServiceStatusSection(trimmedReportUnit, rowMap, provinceUnits);
        insightVO.setAcquisition(acquisitionSection);
        insightVO.setServiceStatus(serviceStatusSection);

        InsightEvaluation evaluation = buildInsightEvaluation(
                acquisitionSection,
                serviceStatusSection,
                rowMap,
                provinceUnits);
        insightVO.setRdAssessment(evaluation.rdAssessment);
        insightVO.setRdScore(evaluation.rdScore);
        insightVO.setServiceStatusAssessment(evaluation.serviceStatusAssessment);
        insightVO.setServiceStatusScore(evaluation.serviceScore);
        insightVO.setRdInsightDescription(evaluation.rdDescription);
        insightVO.setServiceStatusInsightDescription(evaluation.serviceDescription);
        return insightVO;
    }

    private String resolveProvince(String reportUnit) {
        String province = reportUnitMapper.selectProvinceByReportUnit(reportUnit);
        if (!StringUtils.hasText(province)) {
            return UNKNOWN_PROVINCE;
        }
        return province.trim();
    }

    private Set<String> resolveProvinceUnits(String reportUnit, String province) {
        Set<String> provinceUnits = new LinkedHashSet<>();
        provinceUnits.add(reportUnit);
        if (UNKNOWN_PROVINCE.equals(province)) {
            return provinceUnits;
        }
        List<String> units = reportUnitMapper.selectReportUnitsByProvince(province);
        if (units != null) {
            for (String unit : units) {
                if (StringUtils.hasText(unit)) {
                    provinceUnits.add(unit.trim());
                }
            }
        }
        return provinceUnits;
    }

    private List<SoftwareAssetStatisticRow> fetchRows() {
        List<SoftwareAssetStatisticRow> rows = statisticsMapper.selectStatistics();
        return rows == null ? Collections.emptyList() : rows;
    }

    private List<SoftwareAssetProvinceUsageDetail> fetchAllProvinceDetails() {
        List<SoftwareAssetProvinceUsageDetail> details = statisticsMapper.selectAllProvinceUsageDetails();
        return details == null ? Collections.emptyList() : details;
    }

    private SoftwareAssetAcquisitionStatisticVO convertAcquisition(SoftwareAssetStatisticRow row) {
        SoftwareAssetAcquisitionStatisticVO vo = new SoftwareAssetAcquisitionStatisticVO();
        vo.setReportUnit(row.getReportUnit());
        int total = safe(row.getTotalQuantity());
        vo.setTotalQuantity(total);
        vo.setPurchase(buildItem(row.getPurchaseQuantity(), total));
        vo.setSelfDeveloped(buildItem(row.getSelfDevelopedQuantity(), total));
        vo.setCoDeveloped(buildItem(row.getCoDevelopedQuantity(), total));
        vo.setOther(buildItem(row.getOtherQuantity(), total));
        return vo;
    }

    private SoftwareAssetServiceStatusStatisticVO convertServiceStatus(SoftwareAssetStatisticRow row) {
        SoftwareAssetServiceStatusStatisticVO vo = new SoftwareAssetServiceStatusStatisticVO();
        vo.setReportUnit(row.getReportUnit());
        int total = safe(row.getTotalQuantity());
        vo.setTotalQuantity(total);
        vo.setInUse(buildItem(row.getInUseQuantity(), total));
        vo.setIdle(buildItem(row.getIdleQuantity(), total));
        vo.setScrapped(buildItem(row.getScrappedQuantity(), total));
        vo.setClosed(buildItem(row.getClosedQuantity(), total));
        return vo;
    }

    private SoftwareAssetStatisticItemVO buildItem(Integer quantity, int total) {
        SoftwareAssetStatisticItemVO item = new SoftwareAssetStatisticItemVO();
        int safeQuantity = safe(quantity);
        item.setQuantity(safeQuantity);
        item.setPercent(calculatePercent(safeQuantity, total));
        return item;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal calculatePercent(int quantity, int total) {
        if (total <= 0 || quantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(quantity)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private ProvinceAgingAccumulator resolveAccumulator(Map<String, ProvinceAgingAccumulator> accumulatorMap, String provinceName) {
        String resolvedName = (provinceName == null || provinceName.isEmpty()) ? UNKNOWN_PROVINCE : provinceName;
        return accumulatorMap.computeIfAbsent(resolvedName, ProvinceAgingAccumulator::new);
    }

    private static final String IN_USE_STATUS = "在用";
    private static final String CATEGORY_PURCHASE = "购置";
    private static final String CATEGORY_SELF_DEVELOPED = "自主开发";
    private static final String CATEGORY_CO_DEVELOPED = "合作开发";
    private static final String CATEGORY_OTHER = "其他";
    private static final String CATEGORY_IDLE = "闲置";
    private static final String CATEGORY_SCRAPPED = "报废";
    private static final String CATEGORY_CLOSED = "封闭";
    private static final String UNKNOWN_PROVINCE = "其他";

    private InsightEvaluation buildInsightEvaluation(SoftwareAssetDistributionSectionVO acquisitionSection,
                                                     SoftwareAssetDistributionSectionVO serviceStatusSection,
                                                     Map<String, SoftwareAssetStatisticRow> rowMap,
                                                     Set<String> provinceUnits) {
        int acquisitionTotal = sumCompanyTotals(acquisitionSection);
        int selfDeveloped = companyTotal(acquisitionSection, CATEGORY_SELF_DEVELOPED);
        int coDeveloped = companyTotal(acquisitionSection, CATEGORY_CO_DEVELOPED);
        double rdScore = acquisitionTotal <= 0 ? 0.0 : (selfDeveloped + coDeveloped * 0.6) / acquisitionTotal;
        String rdAssessment = evaluateScore(rdScore);

        int serviceTotal = sumCompanyTotals(serviceStatusSection);
        int inUse = companyTotal(serviceStatusSection, IN_USE_STATUS);
        int idle = companyTotal(serviceStatusSection, CATEGORY_IDLE);
        int scrapped = companyTotal(serviceStatusSection, CATEGORY_SCRAPPED);
        int closed = companyTotal(serviceStatusSection, CATEGORY_CLOSED);
        double serviceScore = serviceTotal <= 0 ? 0.0
                : Math.max(0.0, (inUse - idle * 0.15 - scrapped * 0.3 - closed * 0.2) / serviceTotal);
        String serviceAssessment = evaluateScore(serviceScore);

        DimensionSummary acquisitionSummary = calculateDimensionSummary(rowMap, provinceUnits,
                SoftwareAssetStatisticRow::getTotalQuantity);
        DimensionSummary serviceSummary = calculateDimensionSummary(rowMap, provinceUnits,
                SoftwareAssetStatisticRow::getTotalQuantity);

        String rdDescription = buildAcquisitionInsightDescription(acquisitionSection, acquisitionTotal, rdAssessment,
                acquisitionSummary);
        String serviceDescription = buildServiceStatusInsightDescription(serviceStatusSection, serviceTotal,
                serviceAssessment, serviceSummary);

        return new InsightEvaluation(rdAssessment, rdScore, serviceAssessment, serviceScore, rdDescription, serviceDescription);
    }

    private String evaluateScore(double score) {
        if (score >= 0.75) {
            return "优秀";
        }
        if (score >= 0.5) {
            return "良好";
        }
        if (score >= 0.25) {
            return "合格";
        }
        return "较差";
    }

    private String buildAcquisitionInsightDescription(SoftwareAssetDistributionSectionVO acquisitionSection,
                                                      int acquisitionTotal,
                                                      String rdAssessment,
                                                      DimensionSummary dimensionSummary) {

        SoftwareAssetDistributionCategoryVO purchase = findCategory(acquisitionSection, CATEGORY_PURCHASE);
        SoftwareAssetDistributionCategoryVO selfDeveloped = findCategory(acquisitionSection, CATEGORY_SELF_DEVELOPED);
        SoftwareAssetDistributionCategoryVO coDeveloped = findCategory(acquisitionSection, CATEGORY_CO_DEVELOPED);
        SoftwareAssetDistributionCategoryVO other = findCategory(acquisitionSection, CATEGORY_OTHER);

        StringBuilder builder = new StringBuilder();
        builder.append("在自主研发能力方面，现有软件资产").append(acquisitionTotal).append("项")
                .append(formatSummaryStatistics(dimensionSummary)).append("，")
                .append("其中自主开发").append(companyTotal(selfDeveloped)).append("项")
                .append(formatCategoryStatistics(selfDeveloped)).append("，合作开发")
                .append(companyTotal(coDeveloped)).append("项").append(formatCategoryStatistics(coDeveloped))
                .append("，购置").append(companyTotal(purchase)).append("项")
                .append(formatCategoryStatistics(purchase)).append("，其他")
                .append(companyTotal(other)).append("项").append(formatCategoryStatistics(other))
                .append("。综合参考总量、省均值、省中位数及方差，自主研发能力整体")
                .append(resolveAssessmentTone(rdAssessment)).append("，信息化综合评估结果为‘")
                .append(rdAssessment).append("’。");
        return builder.toString();
    }

    private String buildServiceStatusInsightDescription(SoftwareAssetDistributionSectionVO serviceStatusSection,
                                                        int serviceTotal,
                                                        String serviceAssessment,
                                                        DimensionSummary dimensionSummary) {

        SoftwareAssetDistributionCategoryVO inUse = findCategory(serviceStatusSection, IN_USE_STATUS);
        SoftwareAssetDistributionCategoryVO idleCategory = findCategory(serviceStatusSection, CATEGORY_IDLE);
        SoftwareAssetDistributionCategoryVO scrappedCategory = findCategory(serviceStatusSection, CATEGORY_SCRAPPED);
        SoftwareAssetDistributionCategoryVO closedCategory = findCategory(serviceStatusSection, CATEGORY_CLOSED);

        StringBuilder builder = new StringBuilder();
        builder.append("在服务状态方面，共有软件资产").append(serviceTotal).append("项")
                .append(formatSummaryStatistics(dimensionSummary)).append("，在用")
                .append(companyTotal(inUse)).append("项").append(formatCategoryStatistics(inUse))
                .append("，闲置").append(companyTotal(idleCategory)).append("项")
                .append(formatCategoryStatistics(idleCategory)).append("，报废")
                .append(companyTotal(scrappedCategory)).append("项").append(formatCategoryStatistics(scrappedCategory))
                .append("，封闭").append(companyTotal(closedCategory)).append("项")
                .append(formatCategoryStatistics(closedCategory)).append("。整体运行状况")
                .append(resolveAssessmentTone(serviceAssessment)).append("，服务状态综合评估结果为‘")
                .append(serviceAssessment).append("’。");
        return builder.toString();
    }

    private int sumCompanyTotals(SoftwareAssetDistributionSectionVO section) {
        if (section == null || section.getCategories() == null) {
            return 0;
        }
        return section.getCategories().stream()
                .filter(Objects::nonNull)
                .mapToInt(SoftwareAssetDistributionCategoryVO::getCompanyTotal)
                .sum();
    }

    private int companyTotal(SoftwareAssetDistributionSectionVO section, String categoryName) {
        return companyTotal(findCategory(section, categoryName));
    }

    private int companyTotal(SoftwareAssetDistributionCategoryVO categoryVO) {
        return categoryVO == null ? 0 : categoryVO.getCompanyTotal();
    }

    private DimensionSummary calculateDimensionSummary(Map<String, SoftwareAssetStatisticRow> rowMap,
                                                       Set<String> provinceUnits,
                                                       Function<SoftwareAssetStatisticRow, Integer> extractor) {
        List<Integer> values = new ArrayList<>();
        if (provinceUnits != null) {
            for (String unit : provinceUnits) {
                SoftwareAssetStatisticRow row = rowMap == null ? null : rowMap.get(unit);
                values.add(safe(extractValue(row, extractor)));
            }
        }
        return new DimensionSummary(
                StatisticsCalculator.mean(values),
                StatisticsCalculator.median(values),
                StatisticsCalculator.variance(values));
    }

    private String formatSummaryStatistics(DimensionSummary summary) {
        if (summary == null) {
            return "（省均值0、省中位数0、省方差0）";
        }
        return new StringBuilder()
                .append("（省均值").append(formatStatistic(summary.mean()))
                .append("、省中位数").append(formatStatistic(summary.median()))
                .append("、省方差").append(formatStatistic(summary.variance()))
                .append("）").toString();
    }

    private String formatCategoryStatistics(SoftwareAssetDistributionCategoryVO categoryVO) {
        if (categoryVO == null) {
            return "（省均值0、省中位数0、省方差0）";
        }
        return new StringBuilder()
                .append("（省均值").append(formatStatistic(categoryVO.getProvinceMean()))
                .append("、省中位数").append(formatStatistic(categoryVO.getProvinceMedian()))
                .append("、省方差").append(formatStatistic(categoryVO.getProvinceVariance()))
                .append(")").toString();
    }

    private String formatStatistic(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String resolveAssessmentTone(String assessment) {
        if ("优秀".equals(assessment)) {
            return "表现突出";
        }
        if ("良好".equals(assessment)) {
            return "保持良好态势";
        }
        if ("合格".equals(assessment)) {
            return "保持在可控范围";
        }
        return "整体偏弱";
    }

    private SoftwareAssetDistributionCategoryVO findCategory(SoftwareAssetDistributionSectionVO section, String name) {
        if (section == null || section.getCategories() == null) {
            return null;
        }
        for (SoftwareAssetDistributionCategoryVO category : section.getCategories()) {
            if (category != null && Objects.equals(name, category.getName())) {
                return category;
            }
        }
        return null;
    }

    private record InsightEvaluation(String rdAssessment,
                                     double rdScore,
                                     String serviceStatusAssessment,
                                     double serviceScore,
                                     String rdDescription,
                                     String serviceDescription) {
    }

    private record DimensionSummary(BigDecimal mean, BigDecimal median, BigDecimal variance) {
    }

    private SoftwareAssetDistributionSectionVO buildAcquisitionSection(String reportUnit,
                                                                       Map<String, SoftwareAssetStatisticRow> rowMap,
                                                                       Set<String> provinceUnits) {
        SoftwareAssetDistributionSectionVO section = new SoftwareAssetDistributionSectionVO();
        section.setTitle("自主研发能力");
        List<SoftwareAssetDistributionCategoryVO> categories = new ArrayList<>();
        categories.add(buildCategory(CATEGORY_PURCHASE, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getPurchaseQuantity));
        categories.add(buildCategory(CATEGORY_SELF_DEVELOPED, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getSelfDevelopedQuantity));
        categories.add(buildCategory(CATEGORY_CO_DEVELOPED, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getCoDevelopedQuantity));
        categories.add(buildCategory(CATEGORY_OTHER, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getOtherQuantity));
        section.setCategories(categories);
        return section;
    }

    private SoftwareAssetDistributionSectionVO buildServiceStatusSection(String reportUnit,
                                                                         Map<String, SoftwareAssetStatisticRow> rowMap,
                                                                         Set<String> provinceUnits) {
        SoftwareAssetDistributionSectionVO section = new SoftwareAssetDistributionSectionVO();
        section.setTitle("服务状态");
        List<SoftwareAssetDistributionCategoryVO> categories = new ArrayList<>();
        categories.add(buildCategory(IN_USE_STATUS, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getInUseQuantity));
        categories.add(buildCategory(CATEGORY_IDLE, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getIdleQuantity));
        categories.add(buildCategory(CATEGORY_SCRAPPED, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getScrappedQuantity));
        categories.add(buildCategory(CATEGORY_CLOSED, reportUnit, rowMap, provinceUnits, SoftwareAssetStatisticRow::getClosedQuantity));
        section.setCategories(categories);
        return section;
    }

    private SoftwareAssetDistributionCategoryVO buildCategory(String name,
                                                              String reportUnit,
                                                              Map<String, SoftwareAssetStatisticRow> rowMap,
                                                              Set<String> provinceUnits,
                                                              Function<SoftwareAssetStatisticRow, Integer> extractor) {
        SoftwareAssetStatisticRow row = rowMap.get(reportUnit);
        int companyTotal = safe(extractValue(row, extractor));

        List<Integer> provinceValues = new ArrayList<>();
        for (String unit : provinceUnits) {
            SoftwareAssetStatisticRow unitRow = rowMap.get(unit);
            provinceValues.add(safe(extractValue(unitRow, extractor)));
        }

        int provinceTotal = provinceValues.stream().mapToInt(Integer::intValue).sum();

        SoftwareAssetDistributionCategoryVO categoryVO = new SoftwareAssetDistributionCategoryVO();
        categoryVO.setName(name);
        categoryVO.setCompanyTotal(companyTotal);
        categoryVO.setProvinceTotal(provinceTotal);
        categoryVO.setProvinceMean(StatisticsCalculator.mean(provinceValues));
        categoryVO.setProvinceMedian(StatisticsCalculator.median(provinceValues));
        categoryVO.setProvinceVariance(StatisticsCalculator.variance(provinceValues));
        return categoryVO;
    }

    private Integer extractValue(SoftwareAssetStatisticRow row,
                                 Function<SoftwareAssetStatisticRow, Integer> extractor) {
        if (row == null || extractor == null) {
            return 0;
        }
        Integer value = extractor.apply(row);
        return value == null ? 0 : value;
    }

    private static class ProvinceAgingAccumulator {

        private final String province;
        private int inUseTotal;
        private int inUseUpgrade;
        private int notInUseTotal;
        private int notInUseUpgrade;

        private ProvinceAgingAccumulator(String province) {
            this.province = province;
        }

        private void add(boolean inUse, int quantity, boolean requiresUpgrade) {
            if (inUse) {
                inUseTotal += quantity;
                if (requiresUpgrade) {
                    inUseUpgrade += quantity;
                }
            } else {
                notInUseTotal += quantity;
                if (requiresUpgrade) {
                    notInUseUpgrade += quantity;
                }
            }
        }

        private SoftwareAssetAgingStatisticVO toStatistic() {
            SoftwareAssetAgingStatisticVO vo = new SoftwareAssetAgingStatisticVO();
            vo.setProvince(province);
            vo.setInUseTotalQuantity(inUseTotal);
            vo.setInUseUpgradeRequiredQuantity(inUseUpgrade);
            vo.setInUseAgingRatio(SoftwareAssetAgingCalculator.calculateAgingRatio(inUseUpgrade, inUseTotal));
            vo.setNotInUseTotalQuantity(notInUseTotal);
            vo.setNotInUseUpgradeRequiredQuantity(notInUseUpgrade);
            vo.setNotInUseAgingRatio(SoftwareAssetAgingCalculator.calculateAgingRatio(notInUseUpgrade, notInUseTotal));
            return vo;
        }
    }
}
