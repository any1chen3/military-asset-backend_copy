package com.military.asset.controller;

import com.alibaba.excel.EasyExcel;
import com.military.asset.service.SoftwareAssetService;
import com.military.asset.service.CyberAssetService;
import com.military.asset.service.DataContentAssetService;
import com.military.asset.listener.SoftwareAssetExcelListener;
import com.military.asset.listener.CyberAssetExcelListener;
import com.military.asset.listener.DataContentAssetExcelListener;
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.ImportResult;
import com.military.asset.vo.excel.SoftwareAssetExcelVO;
import com.military.asset.vo.excel.CyberAssetExcelVO;
import com.military.asset.vo.excel.DataContentAssetExcelVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import jakarta.servlet.http.HttpServletResponse;



import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
// 🆕 新增import（用于转换方法）
import org.springframework.beans.BeanUtils;
import com.military.asset.entity.SoftwareAsset;
import com.military.asset.entity.CyberAsset;
import com.military.asset.entity.DataContentAsset;
import java.time.LocalDateTime;



/**
 * 资产导入控制器 - 清空再导入版本（简化结果对象）
 * 🎯 核心变更：
    （1） 修改导入逻辑：从"增量导入"改为"全量覆盖导入"
        * 1. 清空操作：导入前清空对应资产表 + 重置上报单位表状态
        * 2. 数据校验：只导入Excel中校验通过的数据
        * 3. 省市同步：批量自动填充省市信息并同步到上报单位表
        * 4. 状态维护：重新设置上报单位表的状态标志
        * 特别注意：
        * - 软件资产表：没有省市字段，省市信息完全通过上报单位表管理
        * - 网信/数据资产表：有省市字段，Excel有值优先，无值自动推导
        * - 上报单位表：只重置对应资产表的状态，不清空整个表

         * 模板文件配置：
         * - 软件资产模板：classpath:templates/software_asset_template.xlsx
         * - 网信资产模板：classpath:templates/cyber_asset_template.xlsx
         * - 数据内容资产模板：classpath:templates/data_content_asset_template.xlsx


    （2）. 修改结果对象：移除重复相关字段（skipCount、duplicatesSkipped、duplicateDetails）
         * 新的导入结果结构：
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
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/asset/import")
@SuppressWarnings("unused")
public class AssetImportController {

    @Autowired
    private SoftwareAssetService softwareAssetService;

    @Autowired
    private CyberAssetService cyberAssetService;

    @Autowired
    private DataContentAssetService dataContentAssetService;


    // ============================ 模板文件路径常量 ============================


    /**
     * 软件资产模板文件路径
     * 位置：src/main/resources/templates/software_asset_template.xlsx
     */
    private static final String SOFTWARE_TEMPLATE_PATH = "templates/software_asset_template.xlsx";

    /**
     * 网信资产模板文件路径
     * 位置：src/main/resources/templates/cyber_asset_template.xlsx
     */
    private static final String CYBER_TEMPLATE_PATH = "templates/cyber_asset_template.xlsx";

    /**
     * 数据内容资产模板文件路径
     * 位置：src/main/resources/templates/data_content_asset_template.xlsx
     */
    private static final String DATA_CONTENT_TEMPLATE_PATH = "templates/data_content_asset_template.xlsx";

    /**
     * 软件资产Excel导入 - 清空再导入版本

     * 🆕 新的处理流程：
     * 1. 文件校验 → 2. 清空软件资产表 → 3. 重置上报单位表软件状态 → 4. 读取Excel → 5. 批量保存并同步省市

     * 💡 关键变化说明：
     * - 清空software_asset表：确保导入数据是唯一数据源
     * - 重置report_unit表software_status=0：清理软件资产状态，不影响其他资产状态
     * - 传入空Map给监听器：因为表已清空，无需检查数据库重复
     * - 调用batchSaveForImport：批量保存并自动同步省市信息

     * 🎯 省市同步逻辑：
     * - 软件资产表没有省市字段，所有省市信息通过上报单位表管理
     * - 根据单位名称批量推导省市信息
     * - 批量更新上报单位表的省市字段和软件状态标志
     *
     * @param file 上传的Excel文件（支持.xlsx和.xls格式，最大100MB）
     * @return ImportResult 包含完整导入结果的响应对象
     */
    @PostMapping("/software")
    public ImportResult importSoftwareAsset(@RequestParam("file") MultipartFile file) {
        log.info("🚀 开始软件资产清空再导入 - 文件名: {}，文件大小: {} bytes",
                file.getOriginalFilename(), file.getSize());
        try {
            // 步骤1：文件基础校验（检查文件格式、大小等）
            validateFile(file);

            // 🆕 步骤2：清空软件资产表并重置上报单位表状态
            log.info("🗑️ 开始清空软件资产表和重置状态...");
            softwareAssetService.clearSoftwareTableAndResetStatus();
            log.info("✅ 软件资产表和状态重置完成");

            // 🆕 步骤3：创建监听器，传入空Map（因为表已清空，无需检查重复）
            // 注意：这里使用HashMap的空实例，而不是获取数据库现有数据
            SoftwareAssetExcelListener listener = new SoftwareAssetExcelListener(new HashMap<>());

            // 步骤4：流式读取Excel文件（不限制行数）
            log.info("📖 开始读取Excel文件内容...");
            EasyExcel.read(file.getInputStream(), SoftwareAssetExcelVO.class, listener)
                    .sheet()
                    .headRowNumber(2) // 跳过表头行
                    .doRead();
            log.info("📊 Excel文件读取完成，有效数据: {}条，错误数据: {}条",
                    listener.getValidDataList().size(), listener.getErrorDataList().size());

            // 🆕 步骤5：批量保存有效数据并同步省市信息
            if (!listener.getValidDataList().isEmpty()) {
                log.info("💾 开始批量保存软件资产数据并同步省市信息...");
                // 🆕 调用新的批量保存方法（支持省市自动填充和上报单位表同步）
                // 🆕 新增：转换ExcelVO为实体
                List<SoftwareAsset> entities = convertToSoftwareEntities(listener.getValidDataList());
                softwareAssetService.batchSaveForImport(entities);
                log.info("✅ 软件资产导入成功保存{}条数据，省市信息同步完成", listener.getValidDataList().size());
            } else {
                log.info("ℹ️ 软件资产导入无有效数据需要保存");
            }

            // 步骤6：构建并返回完整的导入结果
            ImportResult result = buildImportResult(listener, "软件资产");
            log.info("🎉 软件资产清空再导入流程完成");
            return result;

        } catch (Exception e) {
            log.error("❌ 软件资产导入失败: {}", e.getMessage(), e);
            return buildErrorResult("软件资产导入失败: " + e.getMessage());
        }
    }

    /**
     * 网信资产Excel导入 - 清空再导入版本*
     * 🆕 新的处理流程：
     * 1. 文件校验 → 2. 清空网信资产表 → 3. 重置上报单位表网信状态 → 4. 读取Excel → 5. 批量保存并同步省市

     * 💡 网信资产特殊处理：
     * - 有省市字段：Excel有值优先使用，无值自动推导
     * - 资产内容字段：必须填写，格式校验
     * - 已用数量校验：必须≤实有数量

     * 🎯 省市同步逻辑：
     * - 检查Excel中的省市字段：有值保留，无值自动推导
     * - 批量更新上报单位表的省市字段和网信状态标志
     * - 相同单位的省市信息保持一致
     *
     * @param file 上传的Excel文件（支持.xlsx和.xls格式，最大100MB）
     * @return ImportResult 包含完整导入结果的响应对象
     */
    @PostMapping("/cyber")
    public ImportResult importCyberAsset(@RequestParam("file") MultipartFile file) {
        log.info("🚀 开始网信资产清空再导入 - 文件名: {}，文件大小: {} bytes",
                file.getOriginalFilename(), file.getSize());
        try {
            // 步骤1：文件基础校验
            validateFile(file);

            // 🆕 步骤2：清空网信资产表并重置上报单位表状态
            log.info("🗑️ 开始清空网信资产表和重置状态...");
            cyberAssetService.clearCyberTableAndResetStatus();
            log.info("✅ 网信资产表和状态重置完成");

            // 🆕 步骤3：创建监听器，传入空Map
            CyberAssetExcelListener listener = new CyberAssetExcelListener(new HashMap<>());

            // 步骤4：流式读取Excel文件
            log.info("📖 开始读取Excel文件内容...");
            EasyExcel.read(file.getInputStream(), CyberAssetExcelVO.class, listener)
                    .sheet()
                    .headRowNumber(2) // 跳过表头行
                    .doRead();
            log.info("📊 Excel文件读取完成，有效数据: {}条，错误数据: {}条",
                    listener.getValidDataList().size(), listener.getErrorDataList().size());

            // 🆕 步骤5：批量保存有效数据并同步省市信息
            if (!listener.getValidDataList().isEmpty()) {
                log.info("💾 开始批量保存网信资产数据并同步省市信息...");
                // 🆕 调用新的批量保存方法（支持省市自动填充和上报单位表同步）
                // 🆕 新增：转换ExcelVO为实体
                List<CyberAsset> entities = convertToCyberEntities(listener.getValidDataList());
                cyberAssetService.batchSaveForImport(entities);
                log.info("✅ 网信资产导入成功保存{}条数据，省市信息同步完成", listener.getValidDataList().size());
            } else {
                log.info("ℹ️ 网信资产导入无有效数据需要保存");
            }

            // 步骤6：构建并返回完整的导入结果
            ImportResult result = buildImportResult(listener, "网信资产");
            log.info("🎉 网信资产清空再导入流程完成");
            return result;

        } catch (Exception e) {
            log.error("❌ 网信资产导入失败: {}", e.getMessage(), e);
            return buildErrorResult("网信资产导入失败: " + e.getMessage());
        }
    }


    /**
     * 数据内容资产Excel导入 - 清空再导入版本

     * 🆕 新的处理流程：
     * 1. 文件校验 → 2. 清空数据资产表 → 3. 重置上报单位表数据状态 → 4. 读取Excel → 5. 批量保存并同步省市

     * 💡 数据资产特殊处理：
     * - 有省市字段：Excel有值优先使用，无值自动推导
     * - 开发工具字段：必须从固定选项中选择
     * - 盘点单位固定：必须为"保障局"
     * - 更新周期和方式：可选字段，但有固定选项

     * 🎯 省市同步逻辑：
     * - 检查Excel中的省市字段：有值保留，无值自动推导
     * - 批量更新上报单位表的省市字段和数据状态标志
     * - 相同单位的省市信息保持一致
     *
     * @param file 上传的Excel文件（支持.xlsx和.xls格式，最大100MB）
     * @return ImportResult 包含完整导入结果的响应对象
     */
    @PostMapping("/data-content")
    public ImportResult importDataContentAsset(@RequestParam("file") MultipartFile file) {
        log.info("🚀 开始数据内容资产清空再导入 - 文件名: {}，文件大小: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try {
            // 步骤1：文件基础校验
            validateFile(file);

            // 🆕 步骤2：清空数据资产表并重置上报单位表状态
            log.info("🗑️ 开始清空数据内容资产表和重置状态...");
            dataContentAssetService.clearDataContentTableAndResetStatus();
            log.info("✅ 数据内容资产表和状态重置完成");

            // 🆕 步骤3：创建监听器，传入空Map
            DataContentAssetExcelListener listener = new DataContentAssetExcelListener(new HashMap<>());

            // 步骤4：流式读取Excel文件
            log.info("📖 开始读取Excel文件内容...");
            EasyExcel.read(file.getInputStream(), DataContentAssetExcelVO.class, listener)
                    .sheet()
                    .headRowNumber(2) // 跳过表头行
                    .doRead();
            log.info("📊 Excel文件读取完成，有效数据: {}条，错误数据: {}条",
                    listener.getValidDataList().size(), listener.getErrorDataList().size());

            // 🆕 步骤5：批量保存有效数据并同步省市信息
            if (!listener.getValidDataList().isEmpty()) {
                log.info("💾 开始批量保存数据内容资产数据并同步省市信息...");
                // 🆕 调用新的批量保存方法（支持省市自动填充和上报单位表同步）
                // 🆕 新增：转换ExcelVO为实体
                List<DataContentAsset> entities = convertToDataContentEntities(listener.getValidDataList());
                dataContentAssetService.batchSaveForImport(entities);
                log.info("✅ 数据内容资产导入成功保存{}条数据，省市信息同步完成", listener.getValidDataList().size());
            } else {
                log.info("ℹ️ 数据内容资产导入无有效数据需要保存");
            }

            // 步骤6：构建并返回完整的导入结果
            ImportResult result = buildImportResult(listener, "数据内容资产");
            log.info("🎉 数据内容资产清空再导入流程完成");
            return result;

        } catch (Exception e) {
            log.error("❌ 数据内容资产导入失败: {}", e.getMessage(), e);
            return buildErrorResult("数据内容资产导入失败: " + e.getMessage());
        }
    }

    // ============================ 模板下载方法（使用现有模板文件） ============================

    /**
     * 下载软件资产导入模板 - 使用现有模板文件

     * 功能说明：
     * - 从静态资源目录读取现有的软件资产模板文件
     * - 直接返回完整的模板文件，包含示例数据、格式和样式
     * - 支持中文文件名编码，确保下载文件名为中文

     * 模板文件位置：
     * - 源文件：src/main/resources/templates/software_asset_template.xlsx
     * - 打包后：BOOT-INF/classes/templates/software_asset_template.xlsx
     *
     * @param response HTTP响应对象，用于设置下载头信息
     * @throws RuntimeException 当模板文件不存在或读取失败时抛出
     *
     * @apiNote 请确保模板文件存在于指定路径，否则会抛出异常
     */
    @GetMapping("/template/software")
    public void downloadSoftwareTemplate(HttpServletResponse response) {
        String filename = "软件资产导入模板.xlsx";
        try {
            // 设置响应头，触发浏览器下载
            setExcelResponseHeader(response, filename);

            // 从classpath读取现有的模板文件
            Resource resource = new ClassPathResource(SOFTWARE_TEMPLATE_PATH);

            // 检查模板文件是否存在
            if (!resource.exists()) {
                log.error("软件资产模板文件不存在: {}", SOFTWARE_TEMPLATE_PATH);
                throw new RuntimeException("软件资产模板文件不存在，请联系管理员");
            }

            log.info("开始读取软件资产模板文件: {}", SOFTWARE_TEMPLATE_PATH);

            // 将模板文件流写入响应输出流
            try (InputStream inputStream = resource.getInputStream()) {
                long bytesCopied = StreamUtils.copy(inputStream, response.getOutputStream());
                log.info("软件资产模板文件下载完成: {}，文件大小: {} bytes", filename, bytesCopied);
            }

            log.info("软件资产导入模板下载成功: {}", filename);

        } catch (Exception e) {
            log.error("软件资产模板下载失败: {}", e.getMessage(), e);
            throw new RuntimeException("软件资产模板下载失败: " + e.getMessage());
        }
    }

    /**
     * 下载网信资产导入模板 - 使用现有模板文件

     * 功能说明：
     * - 从静态资源目录读取现有的网信资产模板文件
     * - 直接返回完整的模板文件，包含示例数据、格式和样式
     * - 支持中文文件名编码，确保下载文件名为中文

     * 模板文件位置：
     * - 源文件：src/main/resources/templates/cyber_asset_template.xlsx
     * - 打包后：BOOT-INF/classes/templates/cyber_asset_template.xlsx
     *
     * @param response HTTP响应对象，用于设置下载头信息
     * @throws RuntimeException 当模板文件不存在或读取失败时抛出
     *
     * @apiNote 请确保模板文件存在于指定路径，否则会抛出异常
     */
    @GetMapping("/template/cyber")
    public void downloadCyberTemplate(HttpServletResponse response) {
        String filename = "网信资产导入模板.xlsx";
        try {
            // 设置响应头，触发浏览器下载
            setExcelResponseHeader(response, filename);

            // 从classpath读取现有的模板文件
            Resource resource = new ClassPathResource(CYBER_TEMPLATE_PATH);

            // 检查模板文件是否存在
            if (!resource.exists()) {
                log.error("网信资产模板文件不存在: {}", CYBER_TEMPLATE_PATH);
                throw new RuntimeException("网信资产模板文件不存在，请联系管理员");
            }

            log.info("开始读取网信资产模板文件: {}", CYBER_TEMPLATE_PATH);

            // 将模板文件流写入响应输出流
            try (InputStream inputStream = resource.getInputStream()) {
                long bytesCopied = StreamUtils.copy(inputStream, response.getOutputStream());
                log.info("网信资产模板文件下载完成: {}，文件大小: {} bytes", filename, bytesCopied);
            }

            log.info("网信资产导入模板下载成功: {}", filename);

        } catch (Exception e) {
            log.error("网信资产模板下载失败: {}", e.getMessage(), e);
            throw new RuntimeException("网信资产模板下载失败: " + e.getMessage());
        }
    }

    /**
     * 下载数据内容资产导入模板 - 使用现有模板文件

     * 功能说明：
     * - 从静态资源目录读取现有的数据内容资产模板文件
     * - 直接返回完整的模板文件，包含示例数据、格式和样式
     * - 支持中文文件名编码，确保下载文件名为中文

     * 模板文件位置：
     * - 源文件：src/main/resources/templates/data_content_asset_template.xlsx
     * - 打包后：BOOT-INF/classes/templates/data_content_asset_template.xlsx
     *
     * @param response HTTP响应对象，用于设置下载头信息
     * @throws RuntimeException 当模板文件不存在或读取失败时抛出
     *
     * @apiNote 请确保模板文件存在于指定路径，否则会抛出异常
     */
    @GetMapping("/template/data-content")
    public void downloadDataContentTemplate(HttpServletResponse response) {
        String filename = "数据内容资产导入模板.xlsx";
        try {
            // 设置响应头，触发浏览器下载
            setExcelResponseHeader(response, filename);

            // 从classpath读取现有的模板文件
            Resource resource = new ClassPathResource(DATA_CONTENT_TEMPLATE_PATH);

            // 检查模板文件是否存在
            if (!resource.exists()) {
                log.error("数据内容资产模板文件不存在: {}", DATA_CONTENT_TEMPLATE_PATH);
                throw new RuntimeException("数据内容资产模板文件不存在，请联系管理员");
            }

            log.info("开始读取数据内容资产模板文件: {}", DATA_CONTENT_TEMPLATE_PATH);

            // 将模板文件流写入响应输出流
            try (InputStream inputStream = resource.getInputStream()) {
                long bytesCopied = StreamUtils.copy(inputStream, response.getOutputStream());
                log.info("数据内容资产模板文件下载完成: {}，文件大小: {} bytes", filename, bytesCopied);
            }

            log.info("数据内容资产导入模板下载成功: {}", filename);

        } catch (Exception e) {
            log.error("数据内容资产模板下载失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据内容资产模板下载失败: " + e.getMessage());
        }
    }

    // ============================ 辅助方法（保持不变） ============================
    /**
     * 文件参数校验

     * 校验规则：
     * 1. 文件不能为空
     * 2. 文件格式必须是.xlsx或.xls
     * 3. 文件大小不超过100MB（支持大文件导入）
     *
     * @param file 上传的Excel文件
     * @throws IllegalArgumentException 当文件不符合要求时抛出
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException("只支持.xlsx和.xls格式的Excel文件");
        }

        // 100MB文件大小限制（支持大文件导入）
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过100MB");
        }

        log.debug("文件校验通过: {}，大小: {} bytes", filename, file.getSize());
    }

    /**
     * 构建统一的导入结果对象 - 简化版本（移除重复相关字段）
     * 🆕 新的结果结构：
     * - 移除：skipCount（跳过数量）
     * - 移除：duplicatesSkipped（重复跳过数量）
     * - 移除：duplicateDetails（重复详情）

     * 🎯 统计逻辑：
     * - 总行数 = 成功数量 + 错误数量
     * - 只有两种状态：成功 或 错误
     */
    private ImportResult buildImportResult(Object listener, String assetType) {
        try {
            // 🆕 通过反射获取监听器的结果数据（支持不同资产类型的监听器）
            Method getValidDataList = listener.getClass().getMethod("getValidDataList");
            Method getErrorDataList = listener.getClass().getMethod("getErrorDataList");

            // 🆕 获取处理结果数据
            List<?> validDataList = (List<?>) getValidDataList.invoke(listener);
            @SuppressWarnings("unchecked")
            List<ExcelErrorVO> errorDataList = (List<ExcelErrorVO>) getErrorDataList.invoke(listener);

            // 🆕 简化的统计计算
            int totalRows = validDataList.size() + errorDataList.size();
            int successCount = validDataList.size();
            int errorCount = errorDataList.size();

            // 创建基础结果对象
            ImportResult result = new ImportResult();
            result.setSuccess(true);

            // 🆕 简化的消息逻辑
            if (errorCount > 0) {
                result.setMessage(String.format("%s导入完成，成功导入%d条数据，存在%d条错误",
                        assetType, successCount, errorCount));
            } else {
                result.setMessage(String.format("%s导入完成，成功导入%d条数据",
                        assetType, successCount));
            }

            // 构建详细的数据结构
            ImportResult.ImportData data = new ImportResult.ImportData();

            // 🆕 设置基础统计信息（移除skipCount）
            data.setTotalRows(totalRows);
            data.setSuccessCount(successCount);
            data.setErrorCount(errorCount);

            // 🆕 构建导入汇总信息（移除duplicatesSkipped）
            ImportResult.ImportSummary summary = new ImportResult.ImportSummary();
            summary.setTotalProcessed(totalRows);
            summary.setSuccessfullyImported(successCount);
            summary.setCriticalErrors(errorCount);
            data.setImportSummary(summary);

            // 设置错误详情
            data.setErrorDetails(new ArrayList<>(errorDataList));

            // 🆕 移除：不再设置重复详情
            // data.setDuplicateDetails(null);

            // 构建成功记录列表（无数量限制，返回所有成功记录）
            List<ImportResult.SuccessRecord> successRecords = buildSuccessRecords(validDataList);
            data.setSuccessRecords(successRecords);

            // 🆕 设置完整数据到结果对象 （修改输出结果）
            result.setData(data);

            log.info("{}导入结果构建完成: 总处理{}行, 成功{}条, 错误{}条",
                    assetType, totalRows, successCount, errorCount);

            return result;

        } catch (Exception e) {
            log.error("构建导入结果时发生异常: {}", e.getMessage(), e);
            return buildErrorResult("处理导入结果时发生异常");
        }
    }

    /**
     * 构建成功记录列表（无数量限制）

     * 功能说明：
     * - 将有效数据转换为成功记录格式
     * - 支持三种资产类型的VO对象转换
     * - 无数量限制，返回所有成功记录
     * - 支持10万+行数据的完整转换

     * 性能考虑：
     * - 使用Stream处理，内存友好
     * - 异常处理确保单条记录失败不影响整体
     * - 支持大规模数据转换
     *
     * @param validDataList 有效数据列表（从监听器获取）
     * @return List<ImportResult.SuccessRecord> 成功记录列表（无数量限制）
     */
    private List<ImportResult.SuccessRecord> buildSuccessRecords(List<?> validDataList) {
        return validDataList.stream()
                .map(validData -> {
                    ImportResult.SuccessRecord record = new ImportResult.SuccessRecord();
                    try {
                        // 根据资产类型设置相应的字段值
                        if (validData instanceof SoftwareAssetExcelVO softwareVO) {
                            record.setExcelRowNum(softwareVO.getExcelRowNum());
                            record.setAssetId(softwareVO.getId());
                            record.setAssetName(softwareVO.getAssetName());
                            record.setReportUnit(softwareVO.getReportUnit());
                        } else if (validData instanceof CyberAssetExcelVO cyberVO) {
                            record.setExcelRowNum(cyberVO.getExcelRowNum());
                            record.setAssetId(cyberVO.getId());
                            record.setAssetName(cyberVO.getAssetName());
                            record.setReportUnit(cyberVO.getReportUnit());
                        } else if (validData instanceof DataContentAssetExcelVO dataVO) {
                            record.setExcelRowNum(dataVO.getExcelRowNum());
                            record.setAssetId(dataVO.getId());
                            record.setAssetName(dataVO.getAssetName());
                            record.setReportUnit(dataVO.getReportUnit());
                        }
                    } catch (Exception e) {
                        // 单条记录转换失败不影响整体，记录警告日志
                        log.warn("构建成功记录时发生异常: {}", e.getMessage());
                    }
                    return record;
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建错误结果

     * 功能说明：
     * - 创建标准的错误响应对象
     * - 设置错误状态和错误消息
     * - 用于异常情况的统一错误处理
     *
     * @param message 错误消息描述
     * @return ImportResult 错误结果对象
     */
    private ImportResult buildErrorResult(String message) {
        ImportResult result = new ImportResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    /**
     * 设置Excel文件下载响应头

     * 功能说明：
     * - 设置正确的Content-Type和编码
     * - 处理文件名编码，支持中文文件名
     * - 设置下载头信息，触发浏览器下载
     *
     * @param response HTTP响应对象
     * @param filename 下载的文件名
     */
    private void setExcelResponseHeader(HttpServletResponse response, String filename) {
        try {
            // 设置响应内容类型
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            // 处理文件名编码（支持中文）
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + encodedFilename);

            log.debug("设置Excel下载响应头完成: {}", encodedFilename);
        } catch (Exception e) {
            log.error("设置响应头时发生异常: {}", e.getMessage());
            throw new RuntimeException("设置下载响应头失败");
        }
    }

    // ============================ 🆕 新增转换方法（清空再导入专用） ============================

    /**
     * 将SoftwareAssetExcelVO列表转换为SoftwareAsset实体列表
     */
    private List<SoftwareAsset> convertToSoftwareEntities(List<SoftwareAssetExcelVO> voList) {
        return voList.stream()
                .map(vo -> {
                    SoftwareAsset entity = new SoftwareAsset();
                    BeanUtils.copyProperties(vo, entity);
                    entity.setCreateTime(LocalDateTime.now());
                    return entity;
                })
                .collect(Collectors.toList());
    }

    /**
     * 将CyberAssetExcelVO列表转换为CyberAsset实体列表
     */
    private List<CyberAsset> convertToCyberEntities(List<CyberAssetExcelVO> voList) {
        return voList.stream()
                .map(vo -> {
                    CyberAsset entity = new CyberAsset();
                    BeanUtils.copyProperties(vo, entity);
                    entity.setCreateTime(LocalDateTime.now());
                    return entity;
                })
                .collect(Collectors.toList());
    }

    /**
     * 将DataContentAssetExcelVO列表转换为DataContentAsset实体列表
     */
    private List<DataContentAsset> convertToDataContentEntities(List<DataContentAssetExcelVO> voList) {
        return voList.stream()
                .map(vo -> {
                    DataContentAsset entity = new DataContentAsset();
                    BeanUtils.copyProperties(vo, entity);
                    entity.setCreateTime(LocalDateTime.now());
                    return entity;
                })
                .collect(Collectors.toList());
    }
}