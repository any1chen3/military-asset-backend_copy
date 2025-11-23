package com.military.asset.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 批量更新软件资产升级建议时的入参对象。
 */
@Data
@AllArgsConstructor
public class SoftwareRecommendationUpdateItem {

    private String assetId;

    private String recommendation;
}
