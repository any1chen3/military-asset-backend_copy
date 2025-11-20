package com.military.asset.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 数据内容资产Excel导入VO（与数据库data_content_asset表字段1:1映射）
 * 注：删除之前错误添加的`dataFormat`字段（数据库无此字段）
 */
@Data
public class DataContentAssetExcelVO {
    /* 数据库字段：id（VARCHAR(32)，核心列，非空） */
    /** 数据库字段：id（要修改为VARCHAR(50)，核心列，非空） */
    @ExcelProperty("主键")
    private String id;

    /** 数据库字段：report_unit（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("上报单位")
    private String reportUnit;

    /** 数据库字段：province（VARCHAR(50)，非核心列，可选） */
    @ExcelProperty("省")
    private String province;

    /** 数据库字段：city（VARCHAR(50)，非核心列，可选） */
    @ExcelProperty("市")
    private String city;

    /** 数据库字段：category_code（VARCHAR(50)，核心列，非空） */
    @ExcelProperty("分类编码")
    private String categoryCode;

    /** 数据库字段：asset_category（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("资产分类")
    private String assetCategory;

    /** 数据库字段：asset_name（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("资产名称")
    private String assetName;

    /** 数据库字段：data_type（VARCHAR(50)，非核心列，可选） */
    @ExcelProperty("数据类型")
    private String dataType;

    /** 数据库字段：acquisition_method（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("取得方式")
    private String acquisitionMethod;

    /** 数据库字段：function_brief（VARCHAR(1000)，非核心列，可选） */
    @ExcelProperty("功能简介")
    private String functionBrief;

    /** 数据库字段：application_field（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("应用领域")
    private String applicationField;

    /** 数据库字段：development_tool（VARCHAR(150)，核心列，非空，数据特有） */
    @ExcelProperty("开发工具")
    private String developmentTool;

    /** 数据库字段：actual_quantity（INT，核心列，非空） */
    @ExcelProperty("实有数量")
    private Integer actualQuantity;

    /** 数据库字段：unit（VARCHAR(20)，核心列，非空） */
    @ExcelProperty("计量单位")
    private String unit;

    /** 数据库字段：unit_price（DECIMAL(10,2)，非核心列，可选） */
    @ExcelProperty("单价")
    private Double unitPrice;

    /** 数据库字段：amount（DECIMAL(10,2)，非核心列，可选） */
    @ExcelProperty("金额")
    private Double amount;

    /** 数据库字段：pricing_method（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("计价方法")
    private String pricingMethod;

    /** 数据库字段：pricing_description（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("计价说明")
    private String pricingDescription;

    /** 数据库字段：update_cycle（VARCHAR(50)，非核心列，可选） */
    @ExcelProperty("更新周期")
    private String updateCycle;

    /** 数据库字段：update_method（VARCHAR(50)，非核心列，可选） */
    @ExcelProperty("更新方式")
    private String updateMethod;

    /** 数据库字段：inventory_unit（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("盘点单位")
    private String inventoryUnit;

    /** 非数据库字段：Excel行号（用于错误定位） */
    private Integer excelRowNum;
}