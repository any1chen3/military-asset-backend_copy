package com.military.asset.service.impl;

import com.military.asset.entity.DimensionStats;
import com.military.asset.entity.StatisticData;
import com.military.asset.entity.StatisticResult;
import com.military.asset.entity.UnitTotal;
import com.military.asset.mapper.DataContentAssetMapper;
import com.military.asset.vo.CountVO;
import com.military.asset.vo.StatisticVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DataContentAssetService {

    @Autowired
    private DataContentAssetMapper assetMapper;

    /**
     * 核心方法：根据上报单位获取统计结果
     * @param reportUnit 前端传入的上报单位名称
     * @return 包含所有指标的统计结果
     */
    public StatisticResult getStatistic(String reportUnit) {
        // 初始化返回结果
        StatisticResult result = new StatisticResult();
        StatisticData data = result.getData();

        // 1. 第一步：获取该上报单位所在省份（无省份则返回404）
        String province = assetMapper.getProvinceByReportUnit(reportUnit);
        if (province == null || province.trim().isEmpty()) {
            result.setCode(404);
            result.setMessage("未找到[" + reportUnit + "]对应的省份信息");
            return result;
        }

        // 2. 第二步：处理三个维度的统计（总量、均值、中位数、方差）
        processApplicationFieldStat(province, data.getApplicationField());
        processDevelopmentToolStat(province, data.getDevelopmentTool());
        processUpdateMethodStat(province, data.getUpdateMethod());

        // 3. 第三步：统计该上报单位自身的各维度总量
        processUnitSelfTotal(reportUnit, data.getUnitTotal());

        return result;
    }

    /**
     * 处理【应用领域】维度统计
     * @param province 省份
     * @param appFieldStats 应用领域统计结果对象
     */
    private void processApplicationFieldStat(String province, DimensionStats appFieldStats) {
        // 1. 获取该省份下应用领域的“子类别-记录数”列表
        List<CountVO> appFieldCountList = assetMapper.countApplicationFieldByProvince(province);
        if (appFieldCountList.isEmpty()) {
            return; // 无数据则直接返回，避免空指针
        }

        // 2. 收集所有子类别均值（用于后续计算中位数和方差）
        List<BigDecimal> categoryAverages = new ArrayList<>();

        // 3. 为每个子类别计算“总量”和“均值”
        for (CountVO countVO : appFieldCountList) {
            String fieldName = countVO.getDimension(); // 子类别名称（如“后勤保障”）
            Integer totalRecord = countVO.getCount();  // 该类别的总记录数（总量）
            // 统计拥有该类别的上报单位数（去重）
            Integer unitCount = assetMapper.countUnitsByProvinceAndAppField(province, fieldName);

            // 计算均值：总记录数 ÷ 拥有单位数（避免除0，无单位则均值为0）
            BigDecimal categoryAvg = (unitCount > 0)
                    ? BigDecimal.valueOf((double) totalRecord / unitCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // 填充该子类别指标
            appFieldStats.getTotal().put(fieldName, totalRecord);
            appFieldStats.getAverage().put(fieldName, categoryAvg);
            categoryAverages.add(categoryAvg); // 收集均值
        }

        // 4. 基于所有子类别均值，计算“中位数”和“方差”
        appFieldStats.getMedian().putAll(calculateMedian(categoryAverages, "applicationField"));
        appFieldStats.getVariance().putAll(calculateVariance(categoryAverages, "applicationField"));
    }

    /**
     * 处理【开发工具】维度统计
     * @param province 省份
     * @param devToolStats 开发工具统计结果对象
     */
    private void processDevelopmentToolStat(String province, DimensionStats devToolStats) {
        // 1. 获取该省份下开发工具的“子类别-记录数”列表
        List<CountVO> devToolCountList = assetMapper.countDevelopmentToolByProvince(province);
        if (devToolCountList.isEmpty()) {
            return;
        }

        // 2. 收集所有子类别均值
        List<BigDecimal> categoryAverages = new ArrayList<>();

        // 3. 为每个子类别计算“总量”和“均值”
        for (CountVO countVO : devToolCountList) {
            String toolName = countVO.getDimension(); // 子类别名称（如“Oracle”）
            Integer totalRecord = countVO.getCount(); // 该类别的总记录数
            // 统计拥有该工具的上报单位数（去重）
            Integer unitCount = assetMapper.countUnitsByProvinceAndDevTool(province, toolName);

            // 计算均值
            BigDecimal categoryAvg = (unitCount > 0)
                    ? BigDecimal.valueOf((double) totalRecord / unitCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // 填充指标
            devToolStats.getTotal().put(toolName, totalRecord);
            devToolStats.getAverage().put(toolName, categoryAvg);
            categoryAverages.add(categoryAvg);
        }

        // 4. 计算中位数和方差
        devToolStats.getMedian().putAll(calculateMedian(categoryAverages, "developmentTool"));
        devToolStats.getVariance().putAll(calculateVariance(categoryAverages, "developmentTool"));
    }

    /**
     * 处理【更新方式】维度统计
     * @param province 省份
     * @param updateMethodStats 更新方式统计结果对象
     */
    private void processUpdateMethodStat(String province, DimensionStats updateMethodStats) {
        // 1. 获取该省份下更新方式的“子类别-记录数”列表
        List<CountVO> updateMethodCountList = assetMapper.countUpdateMethodByProvince(province);
        if (updateMethodCountList.isEmpty()) {
            return;
        }

        // 2. 收集所有子类别均值
        List<BigDecimal> categoryAverages = new ArrayList<>();

        // 3. 为每个子类别计算“总量”和“均值”
        for (CountVO countVO : updateMethodCountList) {
            String methodName = countVO.getDimension(); // 子类别名称（如“在线填报”）
            Integer totalRecord = countVO.getCount();   // 该类别的总记录数
            // 统计拥有该更新方式的上报单位数（去重）
            Integer unitCount = assetMapper.countUnitsByProvinceAndUpdateMethod(province, methodName);

            // 计算均值
            BigDecimal categoryAvg = (unitCount > 0)
                    ? BigDecimal.valueOf((double) totalRecord / unitCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // 填充指标
            updateMethodStats.getTotal().put(methodName, totalRecord);
            updateMethodStats.getAverage().put(methodName, categoryAvg);
            categoryAverages.add(categoryAvg);
        }

        // 4. 计算中位数和方差
        updateMethodStats.getMedian().putAll(calculateMedian(categoryAverages, "updateMethod"));
        updateMethodStats.getVariance().putAll(calculateVariance(categoryAverages, "updateMethod"));
    }

    /**
     * 统计【上报单位自身】的各维度总量
     * @param reportUnit 上报单位名称
     * @param unitTotal 上报单位自身总量对象
     */
    private void processUnitSelfTotal(String reportUnit, UnitTotal unitTotal) {
        // 1. 应用领域自身总量
        List<CountVO> appFieldSelfList = assetMapper.countUnitApplicationField(reportUnit);
        unitTotal.setApplicationFieldTotal(
                appFieldSelfList.stream().collect(
                        Collectors.toMap(CountVO::getDimension, CountVO::getCount)
                )
        );

        // 2. 开发工具自身总量
        List<CountVO> devToolSelfList = assetMapper.countUnitDevelopmentTool(reportUnit);
        unitTotal.setDevelopmentToolTotal(
                devToolSelfList.stream().collect(
                        Collectors.toMap(CountVO::getDimension, CountVO::getCount)
                )
        );

        // 3. 更新方式自身总量
        List<CountVO> updateMethodSelfList = assetMapper.countUnitUpdateMethod(reportUnit);
        unitTotal.setUpdateMethodTotal(
                updateMethodSelfList.stream().collect(
                        Collectors.toMap(CountVO::getDimension, CountVO::getCount)
                )
        );
    }

    /**
     * 计算中位数（基于所有子类别均值）
     * @param averages 子类别均值列表
     * @param dimensionType 维度类型（用于兼容返回格式，所有子类别共享同一个中位数）
     * @return 中位数Map（key：维度类型标识，value：中位数）
     */
    private java.util.Map<String, Integer> calculateMedian(List<BigDecimal> averages, String dimensionType) {
        java.util.Map<String, Integer> medianMap = new java.util.HashMap<>();
        if (averages.isEmpty()) {
            medianMap.put(dimensionType + "_median", 0);
            return medianMap;
        }

        // 1. 对均值列表排序
        List<BigDecimal> sortedAverages = averages.stream()
                .sorted(BigDecimal::compareTo)
                .collect(Collectors.toList());

        // 2. 计算中位数（四舍五入取整，符合业务数据整数特性）
        int size = sortedAverages.size();
        BigDecimal median;
        if (size % 2 == 1) {
            // 奇数：取中间值
            median = sortedAverages.get(size / 2);
        } else {
            // 偶数：取中间两个值的平均
            BigDecimal mid1 = sortedAverages.get(size / 2 - 1);
            BigDecimal mid2 = sortedAverages.get(size / 2);
            median = mid1.add(mid2).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
        }

        // 3. 转换为整数存入Map（所有子类别共享同一个中位数，用维度类型标识key）
        medianMap.put(dimensionType + "_median", median.intValue());
        return medianMap;
    }

    /**
     * 计算方差（基于所有子类别均值）
     * @param averages 子类别均值列表
     * @param dimensionType 维度类型（用于兼容返回格式，所有子类别共享同一个方差）
     * @return 方差Map（key：维度类型标识，value：方差）
     */
    private java.util.Map<String, BigDecimal> calculateVariance(List<BigDecimal> averages, String dimensionType) {
        java.util.Map<String, BigDecimal> varianceMap = new java.util.HashMap<>();
        int size = averages.size();
        if (size < 2) {
            varianceMap.put(dimensionType + "_variance", BigDecimal.ZERO);
            return varianceMap;
        }

        // 1. 计算所有子类别均值的“平均值”（即均值的均值）
        BigDecimal totalAvg = averages.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(size), 4, RoundingMode.HALF_UP);

        // 2. 计算每个均值与“均值的均值”的平方和
        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal avg : averages) {
            BigDecimal diff = avg.subtract(totalAvg); // 差值
            BigDecimal diffSquare = diff.multiply(diff); // 差值平方
            varianceSum = varianceSum.add(diffSquare); // 平方和累加
        }

        // 3. 计算方差（平方和 ÷ 子类别数量，保留2位小数）
        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(size), 2, RoundingMode.HALF_UP);

        // 4. 存入Map（所有子类别共享同一个方差，用维度类型标识key）
        varianceMap.put(dimensionType + "_variance", variance);
        return varianceMap;
    }
}
