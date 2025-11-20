package com.military.asset.vo.stat;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 软件资产统计视图对象，向前端返回符合展示格式的数据。
 */
@Data
public class SoftwareAssetStatisticVO {

    /** 上报单位 */
    private String reportUnit;

    /** 实有数量合计 */
    private Integer totalQuantity;

    /** 取得方式统计 */
    private AcquisitionStatistic acquisition;

    /** 服务状态统计 */
    private ServiceStatusStatistic serviceStatus;

    @Data
    public static class AcquisitionStatistic {
        private StatisticItem purchase;/*购买*/
        private StatisticItem selfDeveloped;/*自己开发*/
        private StatisticItem coDeveloped;/*合作开发*/
        private StatisticItem other;/*其他*/
    }

    @Data
    public static class ServiceStatusStatistic {
        private StatisticItem inUse;/*在用*/
        private StatisticItem idle;/*闲置*/
        private StatisticItem scrapped;/*报废*/
        private StatisticItem closed;/*封闭*/
    }

    @Data
    public static class StatisticItem {
        private Integer quantity;/*数量*/
        private BigDecimal percent;/*百分比*/
    }
}
