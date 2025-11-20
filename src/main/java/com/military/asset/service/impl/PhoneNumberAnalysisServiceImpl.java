package com.military.asset.service.impl;

import com.military.asset.mapper.CyberAssetMapper;
import com.military.asset.service.PhoneNumberAnalysisService;
import com.military.asset.vo.PhoneNumberAnalysisVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 电话号码分类分析服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberAnalysisServiceImpl implements PhoneNumberAnalysisService {

    private static final List<String> PHONE_CATEGORIES = Arrays.asList(
            "人工电话号码",
            "自动电话号码",
            "移动手机号码",
            "保密电话号码"
    );

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CyberAssetMapper cyberAssetMapper;

    @Override
    public PhoneNumberAnalysisVO analyze(String reportUnit) {
        if (!StringUtils.hasText(reportUnit)) {
            throw new IllegalArgumentException("上报单位不能为空");
        }

        List<Map<String, Object>> rows = cyberAssetMapper.sumPhoneNumberQuantityByCategory(reportUnit, PHONE_CATEGORIES);
        Map<String, Integer> categoryCountMap = toCountMap(rows);

        int manual = categoryCountMap.getOrDefault("人工电话号码", 0);
        int automatic = categoryCountMap.getOrDefault("自动电话号码", 0);
        int mobile = categoryCountMap.getOrDefault("移动手机号码", 0);
        int secrecy = categoryCountMap.getOrDefault("保密电话号码", 0);

        int total = manual + automatic + mobile + secrecy;

        PhoneNumberAnalysisVO vo = new PhoneNumberAnalysisVO();
        vo.setReportUnit(reportUnit);
        vo.setManualCount(manual);
        vo.setAutomaticCount(automatic);
        vo.setMobileCount(mobile);
        vo.setSecrecyCount(secrecy);
        vo.setTotalCount(total);

        if (total == 0) {
            vo.setServiceIndex(BigDecimal.ZERO);
            vo.setMobilityIndex(BigDecimal.ZERO);
            vo.setSecrecyIndex(BigDecimal.ZERO);
            vo.setServiceScore(BigDecimal.ZERO);
            vo.setMobilityScore(BigDecimal.ZERO);
            vo.setSecrecyScore(BigDecimal.ZERO);
            vo.setType("无电话号码（无法根据号码结构判断单位工作性质）");
            vo.setDataFlag("数据缺失");
            return vo;
        }

        vo.setDataFlag(total < 5 ? "数据量偏少，结果仅供参考" : "数据量正常");

        double rM = manual / (double) total;
        double rA = automatic / (double) total;
        double rP = mobile / (double) total;
        double rS = secrecy / (double) total;

        BigDecimal serviceIndex = scaleFour(0.6 * rM + 0.4 * rA);
        BigDecimal mobilityIndex = scaleFour(rP);
        BigDecimal secrecyIndex = scaleFour(rS);

        vo.setServiceIndex(serviceIndex);
        vo.setMobilityIndex(mobilityIndex);
        vo.setSecrecyIndex(secrecyIndex);

        vo.setServiceScore(scaleTwo(serviceIndex.multiply(HUNDRED)));
        vo.setMobilityScore(scaleTwo(mobilityIndex.multiply(HUNDRED)));
        vo.setSecrecyScore(scaleTwo(secrecyIndex.multiply(HUNDRED)));

        vo.setType(determineType(serviceIndex, mobilityIndex, secrecyIndex));
        return vo;
    }

    private Map<String, Integer> toCountMap(List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return new HashMap<>();
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object category = row.get("assetCategory");
            Object totalQuantity = row.get("totalQuantity");
            if (category instanceof String && totalQuantity != null) {
                try {
                    result.put((String) category, Integer.parseInt(totalQuantity.toString()));
                } catch (NumberFormatException ex) {
                    log.warn("无法解析电话号码数量，assetCategory={}, value={}", category, totalQuantity);
                }
            }
        }
        return result;
    }

    private String determineType(BigDecimal serviceIndex, BigDecimal mobilityIndex, BigDecimal secrecyIndex) {
        double service = serviceIndex.doubleValue();
        double mobility = mobilityIndex.doubleValue();
        double secrecy = secrecyIndex.doubleValue();

        if (secrecy >= 0.30) {
            return "涉密/安全类单位";
        }
        if (service >= 0.45 && secrecy < 0.15 && mobility < 0.35) {
            return "服务窗口型单位";
        }
        if (mobility >= 0.40 && service < 0.45 && secrecy < 0.25) {
            return "外勤执法/机动型单位";
        }
        return "一般行政管理型单位";
    }

    private BigDecimal scaleFour(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleTwo(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
