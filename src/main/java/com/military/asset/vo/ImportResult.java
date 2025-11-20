package com.military.asset.vo;

import lombok.Data;
import java.util.List;

/**
 * 统一导入结果封装类 - 清空再导入版本
 * 🎯 核心变更：移除所有重复相关字段

 * 新的结果结构：
 * {
 *   "success": true,
 *   "message": "软件资产导入完成，成功导入50条数据，存在2条错误",
 *   "data": {
 *     "totalRows": 52,
 *     "successCount": 50,
 *     "errorCount": 2,
 *     "importSummary": {
 *       "totalProcessed": 52,
 *       "successfullyImported": 50,
 *       "criticalErrors": 2
 *     },
 *     "errorDetails": [...],
 *     "successRecords": [...]
 *   }
 * }

 * 🆕 移除的字段：
 * - skipCount（跳过数量）
 * - duplicateDetails（重复详情）
 * - duplicatesSkipped（跳过重复数量）
 * - excelDuplicates（Excel内重复数量）
 * - systemDuplicates（系统重复数量）
 * - 整个DuplicateDetails类
 */
@Data
public class ImportResult {

    /**
     * 导入是否成功
     * 成功标准：
     * - 文件格式正确且可读取
     * - 无系统级异常发生
     * 注意：即使有数据校验错误，只要文件处理完成也算成功
     */
    private boolean success;

    /**
     * 导入结果提示信息
     * 🆕 新的消息格式：
     * - 完全成功："软件资产导入完成，成功导入30条数据"
     * - 有错误："软件资产导入完成，成功导入45条数据，存在3条错误"
     * - 全部错误："软件资产导入完成，成功导入0条数据，存在20条错误"
     */
    private String message;

    /**
     * 详细的导入数据结果
     * 🆕 新的数据结构：
     * - 统计信息（简化版）
     * - 错误详情
     * - 成功记录列表
     * - 移除：重复数据详情
     */
    private ImportData data;

    // ============================ 内部数据类 ============================

    /**
     * 导入数据详情类 - 简化版本
     * 🆕 新的统计逻辑：
     * totalRows = successCount + errorCount
     * 🆕 移除的字段：
     * - skipCount（跳过数量）
     * - duplicateDetails（重复详情）
     */
    @Data
    public static class ImportData {

        /**
         * 总处理行数
         * 🆕 新的计算方式：
         * totalRows = successCount + errorCount
         * 包含：
         * - 成功导入的数据
         * - 有错误的数据
         * 🆕 移除：跳过的重复数据
         */
        private int totalRows;

        /**
         * 成功导入数量
         * 定义：通过所有校验并成功保存到数据库的数据条数
         */
        private int successCount;

        /**
         * 🆕 移除：skipCount字段
         * 原因：清空再导入模式下，没有数据被跳过
         */

        /**
         * 错误数据数量
         * 定义：存在关键错误需要用户修正的数据条数
         */
        private int errorCount;

        /**
         * 导入汇总信息 - 简化版本
         * 🆕 移除：duplicatesSkipped字段
         */
        private ImportSummary importSummary;

        /**
         * 错误详情列表
         * 包含所有校验失败的数据详情：
         * [
         *   {
         *     "excelRowNum": 5,
         *     "fieldName": "分类编码",
         *     "errorMessage": "分类编码与资产分类不匹配",
         *     "errorLevel": "CRITICAL"
         *   },
         *   {
         *     "excelRowNum": 12,
         *     "fieldName": "实有数量",
         *     "errorMessage": "实有数量必须为正整数",
         *     "errorLevel": "CRITICAL"
         *   }
         * ]
         */
        private List<Object> errorDetails;

        /**
         * 🆕 移除：duplicateDetails字段
         * 原因：清空再导入模式下，不存在重复数据
         */

        /**
         * 成功记录列表
         * 限制：最多返回100条记录，避免响应数据过大
         * 用途：便于用户确认导入结果
         */
        private List<SuccessRecord> successRecords;
    }

    /**
     * 导入汇总信息类 - 简化版本
     * 🆕 移除的字段：
     * - duplicatesSkipped（跳过重复数量）
     * - excelDuplicates（Excel内重复数量）
     * - systemDuplicates（系统重复数量）
     */
    @Data
    public static class ImportSummary {

        /**
         * 总处理行数
         * 与ImportData中的totalRows保持一致
         */
        private int totalProcessed;

        /**
         * 成功导入数量
         * 与ImportData中的successCount保持一致
         */
        private int successfullyImported;

        /**
         * 🆕 移除：duplicatesSkipped字段
         * 原因：清空再导入模式下，没有数据被跳过
         */

        /**
         * 关键错误数量
         * 与ImportData中的errorCount保持一致
         */
        private int criticalErrors;

        /**
         * 🆕 移除：excelDuplicates字段
         * 原因：清空再导入模式下，不检查Excel内部重复
         */

        /**
         * 🆕 移除：systemDuplicates字段
         * 原因：清空再导入模式下，数据库已清空，不存在系统重复
         */
    }

    /**
     * 🆕 移除：整个DuplicateDetails类
     * 原因：清空再导入模式下，不存在重复数据，无需重复详情
     */

    /**
     * 成功记录类
     * 记录成功导入的数据基本信息
     * 便于用户确认导入结果
     */
    @Data
    public static class SuccessRecord {

        /**
         * Excel行号
         * 用途：便于用户定位原始数据
         * 注意：从1开始计数
         */
        private int excelRowNum;

        /**
         * 资产ID
         * 用途：唯一标识资产
         */
        private String assetId;

        /**
         * 资产名称
         * 用途：便于用户识别资产
         */
        private String assetName;

        /**
         * 上报单位
         * 用途：标识资产所属单位
         */
        private String reportUnit;
    }
}