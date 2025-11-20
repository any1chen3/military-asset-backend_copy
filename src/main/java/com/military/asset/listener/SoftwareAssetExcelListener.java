package com.military.asset.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.military.asset.entity.SoftwareAsset;
import com.military.asset.utils.CategoryMapUtils;
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.excel.SoftwareAssetExcelVO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.*;

/**
 * 软件资产Excel导入监听器（新逻辑版本）

 * 核心修改：
 * 1. 移除Excel内部重复检查，只检查与数据库的重复
 * 2. 当数据库中存在相同ID时，比较关键字段是否一致
 * 3. 关键字段一致 → 静默跳过，关键字段不一致 → 关键错误
 * 4. 所有重复数据统一在"重复数据统计"中汇总显示

 * 新处理流程：
 * 1. ID基础校验 → 2. 数据库重复检查 → 3. 业务字段校验
 */
@Slf4j
public class SoftwareAssetExcelListener extends AnalysisEventListener<SoftwareAssetExcelVO> {

    // ============================ 核心数据存储 ============================

    /**
     * 系统中已存在的完整资产对象Map
     * Key: 资产ID, Value: 完整的资产对象（用于比较关键字段）
     */
    private final Map<String, SoftwareAsset> existingAssets;

    /**
     * 分类映射表
     */
    private final Map<String, String> categoryMap = CategoryMapUtils.initSoftwareCategoryMap();

    // ============================ 导入结果统计 ============================

    @Getter
    private final List<SoftwareAssetExcelVO> validDataList = new ArrayList<>();

    @Getter
    private final List<ExcelErrorVO> errorDataList = new ArrayList<>();

    /**
     * 系统重复数量（关键字段完全一致）
     */
    @Getter
    private int systemDuplicateCount = 0;

    /**
     * 重复记录详情
     */
    @Getter
    private final List<DuplicateRecord> duplicateRecords = new ArrayList<>();

    // ============================ 业务规则常量 ============================

    private static final List<String> LEGAL_SERVICE_STATUS = Arrays.asList("在用", "闲置","报废","封闭");
//    private static final int MAX_VALID_YEARS = 50;
    private static final String ERROR_LEVEL_CRITICAL = "CRITICAL";
    private static final String ERROR_LEVEL_INFO = "INFO";

    // ============================ 构造函数 ============================

    /**
     * 新构造函数 - 接收完整的资产对象Map用于关键字段比较
     *
     * @param existingAssets 系统中已存在的完整资产对象Map
     */
    public SoftwareAssetExcelListener(Map<String, SoftwareAsset> existingAssets) {
        this.existingAssets = (existingAssets != null) ? existingAssets : new HashMap<>();
        log.info("软件资产Excel监听器初始化完成 - 已加载{}条系统已存在资产", this.existingAssets.size());
    }

    // ============================ 核心处理逻辑 ============================

    /**
     * 每行数据读取处理 - 新逻辑流程
     */
    @Override
    public void invoke(SoftwareAssetExcelVO excelVO, AnalysisContext context) {
        int rowNum = context.readRowHolder().getRowIndex() + 1;
        excelVO.setExcelRowNum(rowNum);

        List<String> errorFields = new ArrayList<>();
        StringBuilder errorMsg = new StringBuilder();

        try {
            // 步骤1：ID基础校验
            if (!validateIdFormat(excelVO, rowNum, errorFields, errorMsg)) {
                ExcelErrorVO errorVO = createErrorVO(rowNum, String.join(",", errorFields), errorMsg.toString(), ERROR_LEVEL_CRITICAL);
                errorDataList.add(errorVO);
                return;
            }

            String currentId = excelVO.getId().trim();

            // 步骤2：数据库重复检查（新逻辑核心）
            SoftwareAsset existingAsset = existingAssets.get(currentId);
            if (existingAsset != null) {
                // 数据库中存在相同ID，比较关键字段
                if (isKeyFieldsMatch(excelVO, existingAsset)) {
                    // 关键字段完全一致 → 静默跳过（系统重复）
                    log.debug("第{}行数据与系统数据完全重复，跳过导入", rowNum);
                    systemDuplicateCount++;
                    duplicateRecords.add(createDuplicateRecord(excelVO, rowNum, existingAsset));
                    return;
                } else {
                    // 关键字段不一致 → 关键错误（需修正主键）
                    log.debug("第{}行数据ID重复但关键字段不一致，标记为错误", rowNum);
                    errorDataList.add(createKeyFieldMismatchError(excelVO, rowNum, existingAsset));
                    return;
                }
            }

            // 步骤3：业务字段校验（只有通过重复检查后才进行）
            validateCoreFields(excelVO, rowNum, errorFields, errorMsg);
            validateCategory(excelVO, rowNum, errorFields, errorMsg);
            validateSoftwareSpecificRules(excelVO, rowNum, errorFields, errorMsg);

            // 处理校验结果
            if (!errorFields.isEmpty()) {
                ExcelErrorVO errorVO = createErrorVO(rowNum, String.join(",", errorFields), errorMsg.toString(), ERROR_LEVEL_CRITICAL);
                errorDataList.add(errorVO);
            } else {
                // 所有校验通过，添加到有效数据列表
                validDataList.add(excelVO);
                log.debug("第{}行数据校验通过，加入有效数据列表", rowNum);
            }

        } catch (Exception e) {
            log.error("处理第{}行数据时发生异常", rowNum, e);
            errorDataList.add(createSystemError(excelVO, rowNum, e.getMessage()));
        }
    }

    // ============================ 校验方法 ============================

    /**
     * ID格式校验
     */
    private boolean validateIdFormat(SoftwareAssetExcelVO excelVO, int rowNum,
                                     List<String> errorFields, StringBuilder errorMsg) {
        String id = excelVO.getId();

        // 提取通用校验逻辑：检查ID是否为空
        if (id == null || id.trim().isEmpty()) {
            errorFields.add("id");
            errorMsg.append(String.format("第%d行：资产ID为空；", rowNum));
            return false;
        }
        return true;
    }

    /**
     * 比较关键字段是否一致
     */
    private boolean isKeyFieldsMatch(SoftwareAssetExcelVO excelVO, SoftwareAsset existingAsset) {
        boolean reportUnitEqual = Objects.equals(excelVO.getReportUnit(), existingAsset.getReportUnit());
        boolean assetCategoryEqual = Objects.equals(excelVO.getAssetCategory(), existingAsset.getAssetCategory());
        boolean assetNameEqual = Objects.equals(excelVO.getAssetName(), existingAsset.getAssetName());

        boolean result = reportUnitEqual && assetCategoryEqual && assetNameEqual;

        log.debug("关键字段比较 - 行号{}: 上报单位[{}], 资产分类[{}], 资产名称[{}], 总体[{}]",
                excelVO.getExcelRowNum(), reportUnitEqual, assetCategoryEqual, assetNameEqual, result);

        return result;
    }

    /**
     * 创建关键字段不匹配错误
     */
    private ExcelErrorVO createKeyFieldMismatchError(SoftwareAssetExcelVO excelVO, int rowNum, SoftwareAsset existingAsset) {
        ExcelErrorVO errorVO = new ExcelErrorVO();
        errorVO.setExcelRowNum(rowNum);
        errorVO.setErrorFields("资产ID");
        errorVO.setErrorLevel(ERROR_LEVEL_CRITICAL);
        errorVO.setErrorMsg(String.format(
                "资产ID在系统中已存在但关键字段不一致（系统：%s/%s/%s，Excel：%s/%s/%s），请修改该行主键值",
                existingAsset.getReportUnit(), existingAsset.getAssetCategory(), existingAsset.getAssetName(),
                excelVO.getReportUnit(), excelVO.getAssetCategory(), excelVO.getAssetName()
        ));
        errorVO.setAssetId(excelVO.getId());
        errorVO.setAssetName(excelVO.getAssetName());
        return errorVO;
    }

    /**
     * 创建重复记录
     */
    private DuplicateRecord createDuplicateRecord(SoftwareAssetExcelVO excelVO, int rowNum, SoftwareAsset existingAsset) {
        return new DuplicateRecord(
                rowNum,
                0, // 数据库重复没有具体行号
                excelVO.getId(),
                "系统已存在",
                existingAsset.getReportUnit(),
                existingAsset.getAssetCategory(),
                existingAsset.getAssetName()
        );
    }

    /**
     * 核心字段非空校验
     */
    private void validateCoreFields(SoftwareAssetExcelVO excelVO, int rowNum, List<String> errorFields, StringBuilder errorMsg) {
        // 上报单位校验
        if (excelVO.getReportUnit() == null || excelVO.getReportUnit().trim().isEmpty()) {
            errorFields.add("reportUnit");
            errorMsg.append(String.format("第%d行：上报单位为空；", rowNum));
        }

        // 分类编码校验
        if (excelVO.getCategoryCode() == null || excelVO.getCategoryCode().trim().isEmpty()) {
            errorFields.add("categoryCode");
            errorMsg.append(String.format("第%d行：分类编码为空；", rowNum));
        }

        // 资产分类校验
        if (excelVO.getAssetCategory() == null || excelVO.getAssetCategory().trim().isEmpty()) {
            errorFields.add("assetCategory");
            errorMsg.append(String.format("第%d行：资产分类为空；", rowNum));
        }

        // 资产名称校验
        if (excelVO.getAssetName() == null || excelVO.getAssetName().trim().isEmpty()) {
            errorFields.add("assetName");
            errorMsg.append(String.format("第%d行：资产名称为空；", rowNum));
        }

        // 取得方式校验
        if (excelVO.getAcquisitionMethod() == null || excelVO.getAcquisitionMethod().trim().isEmpty()) {
            errorFields.add("acquisitionMethod");
            errorMsg.append(String.format("第%d行：取得方式为空；", rowNum));
        }

        // 部署范围校验
        if (excelVO.getDeploymentScope() == null || excelVO.getDeploymentScope().trim().isEmpty()) {
            errorFields.add("deploymentScope");
            errorMsg.append(String.format("第%d行：部署范围为空；", rowNum));
        }

        // 服务状态校验
        if (excelVO.getServiceStatus() == null || excelVO.getServiceStatus().trim().isEmpty()) {
            errorFields.add("serviceStatus");
            errorMsg.append(String.format("第%d行：服务状态为空；", rowNum));
        }

        // 实有数量校验
        if (excelVO.getActualQuantity() == null) {
            errorFields.add("actualQuantity");
            errorMsg.append(String.format("第%d行：实有数量为空；", rowNum));
        } else if (excelVO.getActualQuantity() < 0) {
            errorFields.add("actualQuantity");
            errorMsg.append(String.format("第%d行：实有数量需为整数（当前：%d）；", rowNum, excelVO.getActualQuantity()));
        }

        // 计量单位校验
        if (excelVO.getUnit() == null || excelVO.getUnit().trim().isEmpty()) {
            errorFields.add("unit");
            errorMsg.append(String.format("第%d行：计量单位为空；", rowNum));
        }

        // 投入使用日期校验
        if (excelVO.getPutIntoUseDate() == null) {
            errorFields.add("putIntoUseDate");
            errorMsg.append(String.format("第%d行：投入使用日期为空；", rowNum));
        }

        // 盘点单位校验
        if (excelVO.getInventoryUnit() == null || excelVO.getInventoryUnit().trim().isEmpty()) {
            errorFields.add("inventoryUnit");
            errorMsg.append(String.format("第%d行：盘点单位为空；", rowNum));
        }
    }

    /**
     * 分类匹配校验
     */
    private void validateCategory(SoftwareAssetExcelVO excelVO, int rowNum, List<String> errorFields, StringBuilder errorMsg) {
        String categoryCode = excelVO.getCategoryCode();
        String assetCategory = excelVO.getAssetCategory();

        // 只有两个字段都不为空时才进行匹配校验
        if (categoryCode != null && !categoryCode.trim().isEmpty() &&
                assetCategory != null && !assetCategory.trim().isEmpty()) {

            String legalCategory = categoryMap.get(categoryCode.trim());

            if (legalCategory == null) {
                errorFields.add("categoryCode");
                errorMsg.append(String.format("第%d行：分类编码非法（当前值：%s）；", rowNum, categoryCode));
            } else if (!legalCategory.equals(assetCategory.trim())) {
                errorFields.add("categoryCode,assetCategory");
                errorMsg.append(String.format("第%d行：分类不匹配（编码%s对应：%s，Excel分类：%s）；",
                        rowNum, categoryCode, legalCategory, assetCategory));
            }
        }
    }

    /**
     * 软件特有规则校验
     */
    private void validateSoftwareSpecificRules(SoftwareAssetExcelVO excelVO, int rowNum, List<String> errorFields, StringBuilder errorMsg) {
        // 服务状态校验
        String serviceStatus = excelVO.getServiceStatus();
        if (serviceStatus != null && !serviceStatus.trim().isEmpty() &&
                !LEGAL_SERVICE_STATUS.contains(serviceStatus.trim())) {
            errorFields.add("serviceStatus");
            errorMsg.append(String.format("第%d行：服务状态非法（仅允许：%s）；",
                    rowNum, String.join("、", LEGAL_SERVICE_STATUS)));
        }

//        // 投入使用日期校验
//        LocalDate putDate = excelVO.getPutIntoUseDate();
//        if (putDate != null) {
//            LocalDate maxDate = LocalDate.now().minusYears(MAX_VALID_YEARS);
//            if (putDate.isBefore(maxDate)) {
//                errorFields.add("putIntoUseDate");
//                errorMsg.append(String.format("第%d行：投入使用日期非法（需>=%s）；", rowNum, maxDate));
//            }
//        }
        // 投入使用日期校验
        LocalDate putDate = excelVO.getPutIntoUseDate();
        if (putDate != null) {
            LocalDate minDate = LocalDate.of(1949, 10, 1);
            if (putDate.isBefore(minDate)) {
                errorFields.add("putIntoUseDate");
                errorMsg.append(String.format("第%d行：投入使用日期非法（需>=%s）；", rowNum, minDate));
            }
        }
    }

    // ============================ 工具方法 ============================

    /**
     * 创建错误VO对象
     */
    private ExcelErrorVO createErrorVO(int rowNum, String errorFields, String errorMsg, String errorLevel) {
        ExcelErrorVO errorVO = new ExcelErrorVO();
        errorVO.setExcelRowNum(rowNum);
        errorVO.setErrorFields(errorFields);
        errorVO.setErrorMsg(errorMsg);
        errorVO.setErrorLevel(errorLevel);
        return errorVO;
    }

    /**
     * 创建系统错误
     */
    private ExcelErrorVO createSystemError(SoftwareAssetExcelVO excelVO, int rowNum, String message) {
        ExcelErrorVO errorVO = new ExcelErrorVO();
        errorVO.setExcelRowNum(rowNum);
        errorVO.setErrorFields("系统");
        errorVO.setErrorLevel(ERROR_LEVEL_CRITICAL);
        errorVO.setErrorMsg("系统错误: " + message);
        errorVO.setAssetId(excelVO.getId());
        errorVO.setAssetName(excelVO.getAssetName());
        return errorVO;
    }

    // ============================ 数据类定义 ============================

    /**
     * 重复记录详情记录类
     */
    public record DuplicateRecord(
            int currentRowNum,
            int duplicateRowNum, // 0表示数据库重复
            String assetId,
            String duplicateType, // "系统已存在"
            String reportUnit,
            String assetCategory,
            String assetName
    ) {}

    // ============================ 结束处理 ============================

    /**
     * 所有数据解析完成后的处理
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        int totalRows = validDataList.size() + errorDataList.size() + systemDuplicateCount;

        log.info("软件资产Excel解析完成：总行数={}，合法={}条，关键错误={}条，系统重复跳过={}条",
                totalRows, validDataList.size(), errorDataList.size(), systemDuplicateCount);

        // 如果有重复数据，添加汇总信息到错误列表开头
        if (systemDuplicateCount > 0) {
            String summaryMsg = String.format("自动跳过%d条重复数据（系统已存在：%d条）",
                    systemDuplicateCount, systemDuplicateCount);

            ExcelErrorVO summaryError = createErrorVO(0, "summary", summaryMsg, ERROR_LEVEL_INFO);
            errorDataList.add(0, summaryError);
        }
    }
}