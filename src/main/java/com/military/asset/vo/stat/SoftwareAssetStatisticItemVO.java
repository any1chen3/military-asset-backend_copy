package com.military.asset.vo.stat;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 通用的统计条目，用于描述数量及占比。
 */
@Data
public class SoftwareAssetStatisticItemVO {

    /** 数量 */
    private Integer quantity;

    /** 百分比 */
    private BigDecimal percent;
}