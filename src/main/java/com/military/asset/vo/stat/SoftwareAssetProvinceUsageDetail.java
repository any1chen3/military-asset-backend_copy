package com.military.asset.vo.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 软件资产按省份、服务状态的明细行数据。
 * <p>
 * 用于承载 Mapper 聚合前的基础字段，方便在服务层做灵活计算。
 * </p>
 */
@Data
public class SoftwareAssetProvinceUsageDetail {

    /** 所属省份（可能为空） */
    private String province;

    /** 服务状态，如“在用”“闲置”等 */
    private String serviceStatus;

    /** 投入使用日期 */
    private LocalDate putIntoUseDate;

    /** 实有数量 */
    private Integer actualQuantity;
}