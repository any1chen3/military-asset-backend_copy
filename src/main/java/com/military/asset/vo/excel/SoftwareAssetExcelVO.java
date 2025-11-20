package com.military.asset.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import java.time.LocalDate;

/**
 * 软件资产Excel导入VO（与数据库software_asset表字段1:1映射）
 * 注：@ExcelProperty(index = X) 对应Excel列序号，需与Excel模板列顺序一致
 */
@Data
public class SoftwareAssetExcelVO {
    /* 数据库字段：id（VARCHAR(32)，核心列，非空） */
    /** 数据库字段：id（要修改为VARCHAR(50)，核心列，非空） */
    @ExcelProperty("主键")
    private String id;

    /** 数据库字段：title（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("标题")
    private String title;

    /** 数据库字段：data_audit_opinion（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("数据审核意见")
    private String dataAuditOpinion;

    /** 数据库字段：report_unit（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("上报单位")
    private String reportUnit;

    /** 数据库字段：category_code（VARCHAR(50)，核心列，非空） */
    @ExcelProperty("分类编码")
    private String categoryCode;

    /** 数据库字段：asset_category（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("资产分类")
    private String assetCategory;

    /** 数据库字段：asset_name（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("资产名称")
    private String assetName;

    /** 数据库字段：acquisition_method（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("取得方式")
    private String acquisitionMethod;

    /** 数据库字段：function_brief（VARCHAR(1000)，非核心列，可选） */
    @ExcelProperty("功能简介")
    private String functionBrief;

    /** 数据库字段：deployment_scope（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("部署范围")
    private String deploymentScope;

    /** 数据库字段：deployment_form（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("部署形式")
    private String deploymentForm;

    /** 数据库字段：bearing_network（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("承载网络")
    private String bearingNetwork;

    /** 数据库字段：software_copyright（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("软件著作权人")
    private String softwareCopyright;

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

    /** 数据库字段：service_status（VARCHAR(20)，核心列，非空，固定“在用/闲置”） */
    @ExcelProperty("服务状态")
    private String serviceStatus;

    /** 数据库字段：put_into_use_date（DATE，核心列，非空，≤1949年10月1日） */
    @ExcelProperty("投入使用日期")
    private LocalDate putIntoUseDate;

    /** 数据库字段：inventory_unit（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("盘点单位")
    private String inventoryUnit;

    /** 非数据库字段：Excel行号（用于错误定位） */
    private Integer excelRowNum;
}