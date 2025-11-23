package com.military.asset.utils;

import com.military.asset.entity.SoftwareAsset;
import com.military.asset.vo.SoftwareUpgradeEvaluationRequest;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 软件升级必要性与建议生成算法工具类。
 * <p>
 * 公式：升级必要性 = 系数 × 安全指标 × 性能指标 × 需求匹配度。
 * 为避免重复实现，将所有算法封装在此工具类中，便于在服务层高性能复用。
 * </p>
 */
public final class SoftwareUpgradeFormulaUtils {

    /**
     * 默认的建议字符长度下限和上限。
     */
    private static final int MIN_RECOMMENDATION_LENGTH = 150;
    private static final int MAX_RECOMMENDATION_LENGTH = 350;

    private static final BigDecimal STRONG_RECOMMEND_THRESHOLD = new BigDecimal("0.5");
    private static final BigDecimal SOFT_RECOMMEND_THRESHOLD = new BigDecimal("0.2");

    private SoftwareUpgradeFormulaUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 计算升级必要性，所有入参均为 0~1 的系数；空值会按照0处理以保证安全。
     *
     * @param coefficient        权重系数
     * @param securityIndicator  安全指标
     * @param performanceIndicator 性能指标
     * @param requirementMatch   需求匹配度
     * @return 升级必要性，保留四位小数
     */
    public static BigDecimal calculateNecessity(BigDecimal coefficient,
                                                BigDecimal securityIndicator,
                                                BigDecimal performanceIndicator,
                                                BigDecimal requirementMatch) {
        BigDecimal safeCoefficient = defaultZero(coefficient);
        BigDecimal safeSecurity = defaultZero(securityIndicator);
        BigDecimal safePerformance = defaultZero(performanceIndicator);
        BigDecimal safeRequirement = defaultZero(requirementMatch);

        return safeCoefficient
                .multiply(safeSecurity)
                .multiply(safePerformance)
                .multiply(safeRequirement)
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 根据升级必要性生成建议文本，并确保字符长度在 150~350 之间。
     *
     * @param assetName 软件资产名称
     * @param necessity 升级必要性（0~1）
     * @return 建议文本
     */
    public static String buildRecommendation(String assetName, BigDecimal necessity) {
        String safeName = StringUtils.hasText(assetName) ? assetName.trim() : "该软件";
        BigDecimal safeNecessity = defaultZero(necessity);

        String recommendation;
        if (safeNecessity.compareTo(STRONG_RECOMMEND_THRESHOLD) >= 0) {
            recommendation = String.format("%s建议升级，建议时间为30~150日内完成。", safeName);
        } else if (safeNecessity.compareTo(SOFT_RECOMMEND_THRESHOLD) >= 0) {
            recommendation = String.format("%s建议升级，建议时间为半年左右。", safeName);
        } else {
            recommendation = String.format("%s当前版本满足主要需求，可持续关注行业版本演进后再评估升级计划。", safeName);
        }

        String detailed = recommendation + " 安全指标、性能指标及需求匹配度已经过综合评估，建议结合单位的上线节奏、测试资源与合规要求排期，"
                + "在升级前完成备份与回滚方案评审，并对关键业务连续性进行验证。";

        if (detailed.length() < MIN_RECOMMENDATION_LENGTH) {
            detailed = padToMinimum(detailed);
        }

        return detailed.length() > MAX_RECOMMENDATION_LENGTH
                ? detailed.substring(0, MAX_RECOMMENDATION_LENGTH)
                : detailed;
    }

    /**
     * 判定是否需要升级，阈值取 0.2（含）以上视为需要排期升级。
     *
     * @param necessity 升级必要性
     * @return true 表示需要升级
     */
    public static boolean needsUpgrade(BigDecimal necessity) {
        return necessity != null && necessity.compareTo(SOFT_RECOMMEND_THRESHOLD) >= 0;
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 基于软件资产信息推导出升级评估的各项指标，用于仅提供上报单位名称的场景。
     */
    public static SoftwareUpgradeEvaluationRequest deriveEvaluationFromAsset(SoftwareAsset asset) {
        SoftwareUpgradeEvaluationRequest req = new SoftwareUpgradeEvaluationRequest();
        if (asset == null) {
            req.setCoefficient(BigDecimal.ZERO);
            req.setSecurityIndicator(BigDecimal.ZERO);
            req.setPerformanceIndicator(BigDecimal.ZERO);
            req.setRequirementMatch(BigDecimal.ZERO);
            return req;
        }

        BigDecimal coefficient = BigDecimal.valueOf(0.8);
        BigDecimal securityIndicator = deriveIndicatorFromStatus(asset.getServiceStatus());
        BigDecimal performanceIndicator = defaultZero(asset.getAmount())
                .divide(BigDecimal.valueOf(100000), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        BigDecimal requirementMatch = defaultZero(asset.getActualQuantity() == null
                ? null
                : BigDecimal.valueOf(asset.getActualQuantity()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                .min(BigDecimal.ONE);

        req.setCoefficient(coefficient);
        req.setSecurityIndicator(securityIndicator);
        req.setPerformanceIndicator(performanceIndicator);
        req.setRequirementMatch(requirementMatch);
        req.setAssetId(asset.getId());
        return req;
    }

    private static BigDecimal deriveIndicatorFromStatus(String serviceStatus) {
        if (!StringUtils.hasText(serviceStatus)) {
            return BigDecimal.valueOf(0.5);
        }
        String normalized = serviceStatus.trim();
        if (normalized.contains("停") || normalized.contains("退") || normalized.contains("废")) {
            return BigDecimal.valueOf(0.2);
        }
        if (normalized.contains("试") || normalized.contains("测")) {
            return BigDecimal.valueOf(0.6);
        }
        return BigDecimal.valueOf(0.8);
    }

    private static String padToMinimum(String content) {
        StringBuilder builder = new StringBuilder(content);
        String appendix = " 请同步做好安全漏洞修复、性能压测和需求回归验证，确保升级过程满足作战与训练场景的稳定性。";
        while (builder.length() < MIN_RECOMMENDATION_LENGTH) {
            builder.append(appendix);
        }
        return builder.toString();
    }
}
