package com.military.asset.entity;

import lombok.Data;

// 数据内容对象
@Data
public class StatisticData {
    private DimensionStats applicationField = new DimensionStats(); // 应用领域
    private DimensionStats developmentTool = new DimensionStats();  // 开发工具
    private DimensionStats updateMethod = new DimensionStats();     // 更新方式
    private UnitTotal unitTotal = new UnitTotal();                  // 上报单位自身总量
}