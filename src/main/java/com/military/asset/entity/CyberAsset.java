package com.military.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 网信基础资产实体类（与数据库`cyber_asset`表1:1映射）
 * 特有字段：`province`/`city`/`support_object`/`used_quantity`（适配网信资产场景）

 * 新增实现HasReportUnitAndProvince接口，支持自动填充省市
 */
@Data
@TableName("cyber_asset") // 绑定网信基础资产表，与数据库表名一致
public class CyberAsset implements HasReportUnitAndProvince{

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
     * 资产内容（对应数据库`asset_content`字段，VARCHAR(200) NOT NULL）
     * 数据库COMMENT：资产内容（核心列，必填，详细描述资产）
     */
    private String assetContent;

    /**
     * 保障对象（对应数据库`support_object`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：保障对象（非核心列，选填）
     */
    private String supportObject;

    /**
     * 实有数量（对应数据库`actual_quantity`字段，INT NOT NULL）
     * 数据库COMMENT：实有数量（核心列，必填，正整数，用于数量统计）
     */
    private Integer actualQuantity;

    /**
     * 计量单位（对应数据库`unit`字段，VARCHAR(20) NOT NULL）
     * 数据库COMMENT：计量单位（核心列，必填，与实有数量配套）
     */
    private String unit;

    /**
     * 已用数量（对应数据库`used_quantity`字段，INT NOT NULL）
     * 数据库COMMENT：已用数量（核心列，必填，正整数，与实有数量联动统计）
     * 关联后续校验：Service层需校验`usedQuantity ≤ actualQuantity`
     */
    private Integer usedQuantity;

    /**
     * 单价（对应数据库`unit_price`字段，DECIMAL(10,2) DEFAULT NULL）
     * 数据库COMMENT：单价（非核心列，选填，金额格式）
     */
    private BigDecimal unitPrice;

    /**
     * 金额（元）（对应数据库`amount`字段，DECIMAL(10,2) DEFAULT NULL）
     * 数据库COMMENT：金额（元）（非核心列，选填，数量×单价）
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
     * 投入使用日期（对应数据库`put_into_use_date`字段，DATE NOT NULL）
     * 数据库COMMENT：投入使用日期（核心列，必填，LocalDate类型匹配）
     */
    private LocalDate putIntoUseDate;

    /**
     * 盘点单位（对应数据库`inventory_unit`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：盘点单位（核心列，必填）
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
     * 原始账备注（对应数据库`original_account_remark`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：原始账备注（非核心列，选填）
     */
    private String originalAccountRemark;

    /**
     * 数据入库时间（对应数据库`create_time`字段，DATETIME DEFAULT CURRENT_TIMESTAMP）
     * 自动填充：插入时生成当前时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}