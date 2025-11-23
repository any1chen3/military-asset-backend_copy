package com.military.asset.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import java.time.LocalDate;

/**
 * 网信资产Excel导入VO（与数据库cyber_asset表字段1:1映射）
 */
@Data
public class CyberAssetExcelVO {
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

    /** 数据库字段：asset_content（VARCHAR(200)，核心列，非空，网信特有） */
    @ExcelProperty("资产内容")
    private String assetContent;

    /** 数据库字段：support_object（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("保障对象")
    private String supportObject;

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
    @ExcelProperty("金额（元）")
    private Double amount;

    /** 数据库字段：pricing_method（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("计价方法")
    private String pricingMethod;

    /** 数据库字段：pricing_description（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("计价说明")
    private String pricingDescription;

    /** 数据库字段：put_into_use_date（DATE，核心列，非空） */
    @ExcelProperty("投入使用日期")
    private LocalDate putIntoUseDate;

    /** 数据库字段：used_quantity（INT，核心列，非空，网信特有，≤actual_quantity） */
    @ExcelProperty("已用数量")
    private Integer usedQuantity;

    /** 数据库字段：inventory_unit（VARCHAR(150)，核心列，非空） */
    @ExcelProperty("盘点单位")
    private String inventoryUnit;

    /** 数据库字段：inventory_remark（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("盘点备注")
    private String inventoryRemark;

    /** 数据库字段：valuation_remark（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("核价备注")
    private String valuationRemark;

    /** 数据库字段：original_account_remark（VARCHAR(150)，非核心列，可选） */
    @ExcelProperty("原始账备注")
    private String originalAccountRemark;

    /** 非数据库字段：Excel行号（用于错误定位） */
    private Integer excelRowNum;
}