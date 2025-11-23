package com.military.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 数据内容资产实体类（与数据库`data_content_asset`表1:1映射）
 * 特有字段：`data_type`/`acquisition_method`/`application_field`/`development_tool`/`update_cycle`/`update_method`（适配数据资产场景）

 * 新增实现HasReportUnitAndProvince接口，支持自动填充省市
 */
@Data
@TableName("data_content_asset") // 绑定数据内容资产表，与数据库表名一致
public class DataContentAsset implements HasReportUnitAndProvince{

    /**
     * 主键ID（对应数据库`id`字段，VARCHAR(32) NOT NULL）
     * 修改主键ID（对应数据库`id`字段，VARCHAR(50) NOT NULL）
     * 数据库COMMENT：主键（Excel中字符数最大为50的数字字母组合，唯一标识，核心字段）
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 上报单位（对应数据库`report_unit`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：上报单位（核心列，必填，用于多维度查询）
     */
    private String reportUnit;

    /**
     * 省（对应数据库`province`字段，VARCHAR(50) DEFAULT NULL）
     * 数据库COMMENT：省（非核心列，选填）
     */
    private String province;

    /**
     * 市（对应数据库`city`字段，VARCHAR(50) DEFAULT NULL）
     * 数据库COMMENT：市（非核心列，选填）
     */
    private String city;

    /**
     * 分类编码（对应数据库`category_code`字段，VARCHAR(50) NOT NULL）
     * 数据库COMMENT：分类编码（核心列，必填，与资产分类关联）
     */
    private String categoryCode;

    /**
     * 资产分类（对应数据库`asset_category`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：资产分类（核心列，必填，与分类编码匹配）
     */
    private String assetCategory;

    /**
     * 资产名称（对应数据库`asset_name`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：资产名称（核心列，必填，主键重复时业务对比字段）
     */
    private String assetName;

    /**
     * 数据类型（对应数据库`data_type`字段，VARCHAR(50) DEFAULT NULL）
     * 数据库COMMENT：数据类型（非核心列，选填，如"结构化"）
     */
    private String dataType;

    /**
     * 取得方式（对应数据库`acquisition_method`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：取得方式（非核心列，选填，如"采集"）
     */
    private String acquisitionMethod;

    /**
     * 功能简述（对应数据库`function_brief`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：功能简述（非核心列，选填，描述资产功能）
     */
    private String functionBrief;

    /**
     * 应用领域（对应数据库`application_field`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：应用领域（非核心列，选填，如"后勤保障"等）
     */
    private String applicationField;

    /**
     * 开发工具（对应数据库`development_tool`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：开发工具（核心列，必填，如"Oracle"/"MySQL"等）
     */
    private String developmentTool;

    /**
     * 实有数量（对应数据库`actual_quantity`字段，INT NOT NULL）
     * 数据库COMMENT：实有数量（核心列，必填，正整数，用于数量统计）
     */
    private Integer actualQuantity;

    /**
     * 计量单位（对应数据库`unit`字段，VARCHAR(20) NOT NULL）
     * 数据库COMMENT：计量单位（核心列，必填，如"GB"/"MB"等）
     */
    private String unit;

    /**
     * 单价（元）（对应数据库`unit_price`字段，DECIMAL(10,2) DEFAULT NULL）
     * 数据库COMMENT：单价（元）（非核心列，选填，金额格式）
     */
    private BigDecimal unitPrice;

    /**
     * 金额（对应数据库`amount`字段，DECIMAL(10,2) DEFAULT NULL）
     * 数据库COMMENT：金额（非核心列，选填，数量×单价）
     */
    private BigDecimal amount;

    /**
     * 计价方法（对应数据库`pricing_method`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：计价方法（非核心列，选填，如"名义金额法"）
     */
    private String pricingMethod;

    /**
     * 计价说明（对应数据库`pricing_description`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：计价说明（非核心列，选填）
     */
    private String pricingDescription;

    /**
     * 更新周期（对应数据库`update_cycle`字段，VARCHAR(50) DEFAULT NULL）
     * 数据库COMMENT：更新周期（非核心列，选填，如"每月"/"每年"等）
     */
    private String updateCycle;

    /**
     * 更新方式（对应数据库`update_method`字段，VARCHAR(50) DEFAULT NULL）
     * 数据库COMMENT：更新方式（非核心列，选填，如"在线填报"等）
     */
    private String updateMethod;

    /**
     * 盘点单位（对应数据库`inventory_unit`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：盘点单位（核心列，必填，负责盘点的单位）
     */
    private String inventoryUnit;
    /**
     * 盘点备注（对应数据库`inventory_remark`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：盘点备注（非核心列，选填）
     */
    private String inventoryRemark;

    /**
     * 核价备注（对应数据库`valuation_remark`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：核价备注（非核心列，选填）
     */
    private String valuationRemark;

    /**
     * 原始帐备注（对应数据库`original_account_remark`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：原始帐备注（非核心列，选填）
     */
    private String originalAccountRemark;

    /**
     * 数据入库时间（对应数据库`create_time`字段，DATETIME DEFAULT CURRENT_TIMESTAMP）
     * 自动填充：插入时生成当前时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}