package com.military.asset.utils;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 上报单位重要性计算工具。
 */
public final class ReportUnitImportanceUtils {

    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("0.65");
    private static final BigDecimal MID_THRESHOLD = new BigDecimal("0.3");
    private static final Map<String, BigDecimal> DEPLOYMENT_SCOPE_SCORE = Map.of(
            "全军", BigDecimal.ONE,
            "战区", new BigDecimal("0.8"),
            "军种", new BigDecimal("0.6"),
            "军以下", new BigDecimal("0.4"),
            "军级单位内部", new BigDecimal("0.2")
    );
    private static final BigDecimal MAX_SCOPE_SIZE = BigDecimal.valueOf(DEPLOYMENT_SCOPE_SCORE.size());

    private ReportUnitImportanceUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 计算平均得分，空列表返回0。
     */
    public static BigDecimal averageScore(List<BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal score : scores) {
            if (score != null) {
                sum = sum.add(score);
            }
        }
        return scores.isEmpty()
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : sum.divide(BigDecimal.valueOf(scores.size()), 4, RoundingMode.HALF_UP);
    }

    /**
     * 根据得分判定重要性等级。
     *
     * @param score       总得分（范围 >=0）。
     * @param scopeCount  参与计算的部署范围数量，用于归一化到 0~1。
     */
    public static String importanceLevel(BigDecimal score, int scopeCount) {
        if (score == null || scopeCount <= 0) {
            return "低";
        }

        // 根据实际参与的部署范围数量进行 0~1 归一化，保证阈值变化能真实影响判级结果。
        BigDecimal normalized = score.divide(BigDecimal.valueOf(scopeCount), 4, RoundingMode.HALF_UP);

        if (normalized.compareTo(HIGH_THRESHOLD) >= 0) {
            return "高";
        }
        if (normalized.compareTo(MID_THRESHOLD) >= 0) {
            return "中";
        }
        return "低";
    }

    /**
     * 生成分析说明。
     */
    public static String buildAdvice(String reportUnit, BigDecimal score, String level, long assetCount) {
        String unit = StringUtils.hasText(reportUnit) ? reportUnit : "该上报单位";
        BigDecimal safeScore = score == null ? BigDecimal.ZERO : score;
        String base = String.format("%s的软件资产总得分为%.4f，重要性等级判定为%s。", unit, safeScore, level);
        String detail;
        switch (level) {
            case "高":
                detail = "该单位的软件资产成熟度高、覆盖面广，建议优先保障其资源与技术支持，维持领先优势。";
                break;
            case "中":
                detail = "该单位的软件资产具备一定基础，建议根据业务优先级逐步升级或优化，以提升整体竞争力。";
                break;
            default:
                detail = "该单位的软件资产得分偏低，可关注基础设施建设与关键应用补齐，循序推进能力提升。";
        }
        String workload = String.format(" 本次计算覆盖%d项资产得分，结论适用于快速批量评估场景。", assetCount);
        return base + detail + workload;
    }

    /**
     * 根据部署范围及数量统计计算上报单位总得分。
     * <p>
     * 规则：
     * 1. 按部署范围获取基础分（全军/战区/军种/军以下/军级单位内部 → 1/0.8/0.6/0.4/0.2）；
     * 2. 统计该部署范围下所有资产的实有数量之和；
     * 3. 对数量做 0~1 归一化（超过 20 记为 1）；
     * 4. 计算分类得分：部署范围分×60% + 数量分×40%；
     * 5. 将所有分类得分求和得到上报单位总分。
     */
    public static BigDecimal calculateImportanceScore(Map<String, Long> scopeQuantities) {
        if (scopeQuantities == null || scopeQuantities.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Long> entry : scopeQuantities.entrySet()) {
            BigDecimal normalized = calculateScopeScore(entry.getKey(), entry.getValue());
            total = total.add(normalized);
        }

        return total.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算单个部署范围的归一化得分。
     */
    public static BigDecimal calculateScopeScore(String scope, long quantity) {
        BigDecimal scopeScore = Optional.ofNullable(scope)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(DEPLOYMENT_SCOPE_SCORE::get)
                .orElse(BigDecimal.ZERO);

        BigDecimal quantityScore = normalizeQuantity(quantity);

        // 数量越大，覆盖面得分和数量得分都会随之抬升，避免出现“全军数量更多却得分更低”的错觉。
        BigDecimal coverageWeighted = scopeScore.multiply(BigDecimal.valueOf(0.6)).multiply(quantityScore);
        BigDecimal quantityWeighted = quantityScore.multiply(BigDecimal.valueOf(0.4));

        return coverageWeighted
                .add(quantityWeighted)
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 归一化数量得分，范围 [0,1]。
     */
    public static BigDecimal normalizeQuantity(long quantity) {
        if (quantity <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (quantity >= 20) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(quantity)
                .divide(BigDecimal.valueOf(20), 4, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
