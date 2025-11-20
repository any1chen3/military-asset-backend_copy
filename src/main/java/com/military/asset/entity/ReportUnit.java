package com.military.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 上报单位表实体类
 */
@Data
@TableName("report_unit") // 与数据库表名一致
public class ReportUnit {
    /**
     * 自动增长主键
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 上报单位名称
     */
    private String reportUnit;

    /**
     * 所属省份（初始为NULL，匹配后填充）
     */
    private String province;

    /**
     * 数据来源表--网信基础资产
     */
    private short source_table_cyber_asset;

    /**
     * 数据来源表--数据内容资产
     */
    private short source_table_data_content_asset;

    /**
     * 数据来源表--软件应用资产
     */
    private short source_table_software_asset;
}