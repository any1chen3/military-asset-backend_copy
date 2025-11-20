package com.military.asset.vo;

public class CountVO {
    private String dimension;  // 维度值（如“后勤保障”“Oracle”）
    private Integer count;     // 该维度的记录条数

    // 无参构造+Getter/Setter
    public CountVO() {}
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}
