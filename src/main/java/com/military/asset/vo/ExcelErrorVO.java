package com.military.asset.vo;

import lombok.Data;

/**
 * 三表共用Excel导入错误报告VO（增强版）
 * 作用：记录Excel导入时违反数据库约束或业务规则的错误，支撑后续前端反馈
 * 新增功能：错误级别分类，优化大量重复数据时的错误报告体验
 * 关联逻辑：错误字段与Excel映射VO、数据库表字段名完全一致（如"usedQuantity"对应数据库`used_quantity`）

 * 新增字段：
 * - assetId: 资产ID，用于错误信息中标识具体资产
 * - assetName: 资产名称，用于错误信息中标识具体资产
 */
@Data
public class ExcelErrorVO {
    /**
     * Excel行号（从1开始计数，与实际Excel行号一致）
     * 作用：用户可直接定位错误数据行，无需逐行排查
     * 特殊值：0表示汇总信息，非具体行号
     */
    private Integer excelRowNum;

    /**
     * 错误字段（多个字段用逗号分隔，如"id,assetName"）
     * 要求：与Excel映射VO、实体类、数据库表字段名一致（如数据库`deployment_scope`→VO`deploymentScope`）
     * 特殊值："summary"表示汇总统计信息
     */
    private String errorFields;

    /**
     * 错误原因（结合数据库约束编写，明确违规类型）
     * 示例："部署范围deploymentScope为空（数据库核心列，NOT NULL）""实有数量actualQuantity非正整数（数据库INT类型）"
     * 汇总信息："自动跳过999条重复数据（Excel内重复：500条，系统已存在：499条）"
     */
    private String errorMsg;

    /**
     * 错误级别（新增字段）
     * CRITICAL: 关键错误，需要用户立即修正（字段为空、格式错误、主键冲突等）
     * INFO: 提示信息，重复数据统计等可忽略的信息
     * 作用：前端可根据错误级别差异化显示，避免大量重复信息淹没关键错误
     */
    private String errorLevel;

    // ============================ 新增字段 ============================

    /**
     * 资产ID
     * 新增用途：在错误信息中标识具体资产，便于用户快速定位
     * 使用场景：关键错误、重复数据等需要明确资产标识的场景
     * 为空情况：汇总信息时可为空
     */
    private String assetId;

    /**
     * 资产名称
     * 新增用途：在错误信息中标识具体资产，便于用户快速识别
     * 使用场景：关键错误、重复数据等需要明确资产标识的场景
     * 为空情况：汇总信息时可为空
     */
    private String assetName;
}