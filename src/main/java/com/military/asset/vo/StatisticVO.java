package com.military.asset.vo;

import java.math.BigDecimal;

public class StatisticVO {
    private String dimension;       // 维度名称（如“后勤保障”“Oracle”）
    private Integer totalCount;     // 总量（记录条数，新字段）
    private BigDecimal average;     // 均值（actual_quantity的平均值）

    // 无参构造+Getter/Setter
    public StatisticVO() {}

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public BigDecimal getAverage() { return average; }
    public void setAverage(BigDecimal average) { this.average = average; }
}