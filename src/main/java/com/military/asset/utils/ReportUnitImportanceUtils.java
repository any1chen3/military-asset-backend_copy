package com.military.asset.utils;

import com.military.asset.entity.SoftwareAsset;
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

    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("0.8");
    private static final BigDecimal MID_THRESHOLD = new BigDecimal("0.5");
    private static final Map<String, BigDecimal> DEPLOYMENT_SCOPE_SCORE = Map.of(
            "全军", BigDecimal.ONE,
            "战区", new BigDecimal("0.8"),
            "军种", new BigDecimal("0.6"),
            "军以下", new BigDecimal("0.4"),
            "军级单位内部", new BigDecimal("0.2")
    );

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
     */
    public static String importanceLevel(BigDecimal score) {
        if (score == null) {
            return "低";
        }
        if (score.compareTo(HIGH_THRESHOLD) >= 0) {
            return "高";
        }
        if (score.compareTo(MID_THRESHOLD) >= 0) {
            return "中";
        }
        return "低";
    }

    /**
     * 生成分析说明。
     */
    public static String buildAdvice(String reportUnit, BigDecimal score, String level, int assetCount) {
        String unit = StringUtils.hasText(reportUnit) ? reportUnit : "该上报单位";
        BigDecimal safeScore = score == null ? BigDecimal.ZERO : score;
        String base = String.format("%s的平均软件资产得分为%.4f，重要性等级判定为%s。", unit, safeScore, level);
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
     * 从软件资产字段推导一个 0~1 之间的得分，用于仅提供上报单位名称时的快速评估。
     * <p>
     * 计算规则：
     * - 部署范围：全军、战区、军种、军以下、军级单位内部依次赋值 1/0.8/0.6/0.4/0.2；
     * - 数量：按 0~100 进行归一化，大于 100 记为 1；
     * - 综合得分：部署范围 60% + 数量 40%，并限制在 [0,1] 区间内。
     * </p>
     */
    public static BigDecimal deriveScoreFromAsset(SoftwareAsset asset) {
        if (asset == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal scopeScore = Optional.ofNullable(asset.getDeploymentScope())
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(DEPLOYMENT_SCOPE_SCORE::get)
                .orElse(BigDecimal.ZERO);

        BigDecimal quantityScore = Optional.ofNullable(asset.getActualQuantity())
                .filter(q -> q > 0)
                .map(q -> BigDecimal.valueOf(q).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);

        if (quantityScore.compareTo(BigDecimal.ONE) > 0) {
            quantityScore = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal weighted = scopeScore.multiply(BigDecimal.valueOf(0.6))
                .add(quantityScore.multiply(BigDecimal.valueOf(0.4)));

        if (weighted.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        if (weighted.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return weighted.setScale(4, RoundingMode.HALF_UP);
    }
}
