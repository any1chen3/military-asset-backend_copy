package com.military.asset.vo;

import lombok.Data;

/**
 * 软件资产查询参数VO
 * 作用：接收前端传递的软件资产查询条件，支持分页和动态条件筛选
 * 特点：所有字段都是可选的，前端可以根据需要传递任意组合的查询条件
 * 使用场景：用于列表查询和导出功能的参数传递
 */
@Data
public class SoftwareQueryVO {
    // 分页参数
    private Integer pageNum;          // 当前页码（可选）
    private Integer pageSize;         // 每页大小（可选）

    // 查询条件字段
    private String reportUnit;        // 上报单位（可选）
    private String categoryCode;      // 分类编码（可选）
    private String assetCategory;     // 资产分类（可选）
    private String acquisitionMethod; // 取得方式（可选）
    private String deploymentScope;   // 部署范围（可选）
    private String deploymentForm;    // 部署形式（可选）
    private String bearingNetwork;    // 承载网络（可选）
    private Integer quantityMin;      // 实有数量最小值（可选）
    private Integer quantityMax;      // 实有数量最大值（可选）
    private String serviceStatus;     // 服务状态（可选）
    private String startUseDateStart; // 投入使用日期开始（可选）
    private String startUseDateEnd;   // 投入使用日期结束（可选）
    private String inventoryUnit;     // 盘点单位（可选）
}