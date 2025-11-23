package com.military.asset.entity;

// MyBatis Plus核心注解：表映射、主键、字段配置（与数据库关联核心依赖）
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
// Lombok注解：自动生成getter/setter/toString，减少重复代码（与VO层一致）
import lombok.Data;
// 金额类型：匹配数据库DECIMAL(10,2)，避免精度误差（如unit_price、amount字段）
import java.math.BigDecimal;
// 日期类型：匹配数据库DATE（如put_into_use_date字段），比传统Date更简洁
import java.time.LocalDate;
// 时间类型：匹配数据库DATETIME（如create_time字段），记录完整时间
import java.time.LocalDateTime;

/**
 * 软件应用资产实体类（与数据库`software_asset`表1:1映射）
 * 字段注释关联数据库COMMENT，核心列标注必填/业务意义

 * 🆕 新增：实现HasReportUnitAndProvince接口，支持自动填充省市
 */
@Data // Lombok注解：无需手动写getter/setter
@TableName("software_asset") // 绑定数据库表名，必须与表名完全一致
public class SoftwareAsset implements HasReportUnitAndProvince { // 🆕 新增：实现接口

    /**
     * 主键ID（对应数据库`id`字段，VARCHAR(32) NOT NULL）
     * 修改主键ID（对应数据库`id`字段，VARCHAR(50) NOT NULL）
     * 数据库COMMENT：主键（Excel中字符数最大为50的数字字母组合，唯一标识，核心字段）
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 标题（对应数据库`title`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：标题（非核心列，选填）
     */
    private String title;

    /**
     * 数据审核意见（对应数据库`data_audit_opinion`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：数据审核意见（非核心列，选填）
     */
    private String dataAuditOpinion;

    /**
     * 上报单位（对应数据库`report_unit`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：上报单位（核心列，必填，用于多维度查询）
     */
    private String reportUnit;

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
     * 关联后续逻辑：主键重复时，与数据库该字段对比，一致则跳过，不一致则报错
     */
    private String assetName;

    /**
     * 取得方式（对应数据库`acquisition_method`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：取得方式（核心列，必填，如"采购"/"自研"等）
     */
    private String acquisitionMethod;

    /**
     * 功能简述（对应数据库`function_brief`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：功能简述（非核心列，选填）
     */
    private String functionBrief;

    /**
     * 部署范围（对应数据库`deployment_scope`字段，VARCHAR(150) NOT NULL）
     * 数据库COMMENT：部署范围（核心列，必填，如"军以下"/"军级"等）
     */
    private String deploymentScope;

    /**
     * 部署形式（对应数据库`deployment_form`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：部署形式（非核心列，选填）
     */
    private String deploymentForm;

    /**
     * 承载网络（对应数据库`bearing_network`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：承载网络（非核心列，选填）
     */
    private String bearingNetwork;

    /**
     * 软件著作权人（对应数据库`software_copyright`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：软件著作权人（非核心列，选填）
     */
    private String softwareCopyright;

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
     * 单价（对应数据库`unit_price`字段，DECIMAL(10,2) DEFAULT NULL）
     * 数据库COMMENT：单价（非核心列，选填，金额格式）
     */
    private BigDecimal unitPrice;

    /**
     * 金额（对应数据库`amount`字段，DECIMAL(10,2) DEFAULT NULL）
     * 数据库COMMENT：金额（非核心列，选填，数量×单价）
     */
    private BigDecimal amount;

    /**
     * 计价方法（对应数据库`pricing_method`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：计价方法（非核心列，选填）
     */
    private String pricingMethod;

    /**
     * 计价说明（对应数据库`pricing_description`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：计价说明（非核心列，选填）
     */
    private String pricingDescription;

    /**
     * 服务状态（对应数据库`service_status`字段，VARCHAR(20) NOT NULL）
     * 数据库COMMENT：服务状态（核心列，必填，如"在用/闲置"）
     */
    private String serviceStatus;

    /**
     * 投入使用日期（对应数据库`put_into_use_date`字段，DATE NOT NULL）
     * 数据库COMMENT：投入使用日期（核心列，必填，≤当前日期-50年）
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
     * 原始帐备注（对应数据库`original_account_remark`字段，VARCHAR(150) DEFAULT NULL）
     * 数据库COMMENT：原始帐备注（非核心列，选填）
     */
    private String originalAccountRemark;

    /**
     * 升级建议（对应数据库`recommendation`字段，VARCHAR(150) DEFAULT NULL）
     * 业务含义：根据软件升级必要性算法生成的文字化建议，便于在表单中直接展示给填报人
     */
    private String recommendation;

    /**
     * 数据入库时间（对应数据库`create_time`字段，DATETIME DEFAULT CURRENT_TIMESTAMP）
     * 数据库COMMENT：数据入库时间（系统自动生成）
     * 自动填充：插入数据时，MyBatis Plus自动填充当前时间，无需手动赋值
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ============================ 🆕 新增：HasReportUnitAndProvince接口实现 ============================

    /**
     * 🆕 新增：省份字段（非数据库字段，仅用于自动填充推导）
     * 软件资产表没有省市字段，但接口需要实现
     * 使用@TableField(exist = false)表示不映射到数据库
     */
    @TableField(exist = false)
    private String province;

    /**
     * 🆕 新增：城市字段（非数据库字段，仅用于自动填充推导）
     * 软件资产表没有省市字段，但接口需要实现
     * 使用@TableField(exist = false)表示不映射到数据库
     */
    @TableField(exist = false)
    private String city;

    // 🆕 新增：实现HasReportUnitAndProvince接口的方法

    @Override
    public String getProvince() {
        return province;
    }

    @Override
    public void setProvince(String province) {
        this.province = province;
    }

    @Override
    public String getCity() {
        return city;
    }

    @Override
    public void setCity(String city) {
        this.city = city;
    }

    // getReportUnit() 方法已经存在，不需要重复实现
}