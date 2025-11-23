package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 上报单位软件资产得分请求。
 * <p>
 * 每条记录代表某个上报单位的一项软件应用资产及其得分，用于后续按上报单位聚合计算重要性。
 * </p>
 */
@Data
public class ReportUnitAssetScoreRequest {

    /**
     * 上报单位名称。
     */
    private String reportUnit;

    /**
     * 软件资产ID，可选，用于追踪来源。
     */
    private String assetId;

    /**
     * 软件资产名称，可选，用于构建说明。
     */
    private String assetName;

    /**
     * 该资产的综合得分（0~1）。
     */
    private BigDecimal assetScore;
}
