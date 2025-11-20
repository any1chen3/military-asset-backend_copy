package com.military.asset.controller;

import com.military.asset.entity.CyberAsset;
import com.military.asset.entity.DataContentAsset;
import com.military.asset.entity.SoftwareAsset;
import com.military.asset.entity.Province;
import com.military.asset.mapper.ProvinceMapper;
import com.military.asset.service.CyberAssetService;
import com.military.asset.service.DataContentAssetService;
import com.military.asset.service.SoftwareAssetService;
import com.military.asset.service.ReportUnitService;
import com.military.asset.vo.ResultVO;
import com.military.asset.vo.stat.ProvinceMetricVO;
import com.military.asset.vo.stat.SoftwareAssetStatisticVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

// 添加以下import语句
import java.util.Map;
import java.util.HashMap;
// 在现有的 import 语句后面添加：
import java.util.LinkedHashMap;
import com.military.asset.utils.CategoryMapUtils;


// ====================1117 导出功能相关import ====================
import jakarta.servlet.http.HttpServletResponse;  // Spring Boot 3.x 使用 jakarta包  // HTTP响应对象
import com.alibaba.excel.EasyExcel;             // EasyExcel核心类
import java.util.stream.Collectors;             // Stream收集器
// Excel VO类
import com.military.asset.vo.excel.SoftwareAssetExcelVO;
import com.military.asset.vo.excel.CyberAssetExcelVO;
import com.military.asset.vo.excel.DataContentAssetExcelVO;
// 查询VO类
import com.military.asset.vo.SoftwareQueryVO;
import com.military.asset.vo.CyberQueryVO;
import com.military.asset.vo.DataContentQueryVO;

// 按省统计
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;


/**
 * 三表统一CRUD控制器 + 首页控制器
 * 适配各表特有约束，统一返回ResultVO
 * 新增功能：首页欢迎页面，提供系统接口说明
 */
@RestController
@RequestMapping("/api/asset")
@Slf4j
@SuppressWarnings("unused") // 抑制IDE误报警告
public class AssetCrudController {

    private final SoftwareAssetService softwareService;
    private final CyberAssetService cyberService;
    private final DataContentAssetService dataService;
    private final ProvinceMapper provinceMapper;
    private final ReportUnitService reportUnitService; // 新增：上报单位服务

    /**
     * 构造器注入（合并两个构造函数）
     */
    @Autowired
    public AssetCrudController(SoftwareAssetService softwareService,
                               CyberAssetService cyberService,
                               DataContentAssetService dataService,
                               ProvinceMapper provinceMapper,
                               ReportUnitService reportUnitService) { // 新增参数
        this.softwareService = softwareService;
        this.cyberService = cyberService;
        this.dataService = dataService;
        this.provinceMapper = provinceMapper;
        this.reportUnitService = reportUnitService; // 新增初始化
    }


    // ============================== 首页欢迎接口 ==============================

    /**
     * 系统首页欢迎接口
     * 访问路径：GET http://localhost:8080/

     * 作用：提供系统概览和所有可用接口的说明文档
     *
     * @return 系统欢迎信息和接口文档
     */
    @GetMapping("/")
    public ResultVO<String> home() {
        String welcomeMessage =
                "🚀 欢迎使用军工资产管理系统 🚀\n\n" +
                        "📊 系统概述：\n" +
                        "   本系统用于管理军工企业的三类核心资产：软件资产、网信资产、数据内容资产\n" +
                        "   支持Excel批量导入、CRUD操作、多条件组合查询等功能\n\n" +

                        "📋 可用接口列表：\n\n" +

                        "📥 Excel导入接口（POST请求，multipart/form-data格式）：\n" +
                        "   • 软件资产导入: /api/asset/import/software\n" +
                        "   • 网信资产导入: /api/asset/import/cyber\n" +
                        "   • 数据资产导入: /api/asset/import/data-content\n\n" +

                        // ============================== 1117新增：统一导出接口 （参数与联合查询一致） ==============================
                        "📤 Excel导出接口：\n" +
                        "   • 软件资产统一导出（POST）: /api/asset/export/software\n" +
                        "   • 网信资产统一导出（POST）: /api/asset/export/cyber\n" +
                        "   • 数据资产统一导出（POST）: /api/asset/export/data\n" +
                        "   支持三种导出模式：\n" +
                        "     - 无查询条件 → 导出全部数据\n" +
                        "     - 有查询条件+无分页 → 导出全部匹配数据\n" +
                        "     - 有查询条件+有分页 → 导出当前页数据\n\n" +

                        // ============================== 新增 ==============================
                        "🔍 查询接口（GET请求）：\n" +
                        "（1） 软件应用资产表接口：\n" +
                        "   • 软件资产详情: /api/asset/software/{id}\n" +
                        "   • 软件资产联合查询: /api/asset/software/combined-query?pageNum=1&pageSize=50&reportUnit=xxx&categoryCode=xxx&assetCategory=xxx&acquisitionMethod=xxx&deploymentScope=xxx&deploymentForm=xxx&bearingNetwork=xxx&quantityMin=xxx&quantityMax=xxx&serviceStatus=xxx&startUseDateStart=xxx&startUseDateEnd=xxx&inventoryUnit=xxx\n" +
                        "   • *软件资产升级判定: /api/asset/software/statistics/v2/aging/asset/{assetId}/upgrade-required\n" +
                        "   • *软件资产自主研发能力与服务状态洞察: /api/asset/software/statistics/v2/report-unit/{reportUnit}/insight\n" +
                        "   • *软件资产取得方式统计: /api/asset/software/statistics/v2/acquisition\n" +
                        "   • *软件资产服务状态统计: /api/asset/software/statistics/v2/service-status\n" +
                        "   • *软件资产省份老化统计: /api/asset/software/statistics/v2/aging/province\n" +
                        "\n" +
                        "（2） 网信基础资产表接口：\n" +
                        "   • 网信资产详情: /api/asset/cyber/{id}\n" +
                        "   • 网信资产联合查询: /api/asset/cyber/combined-query?pageNum=1&pageSize=50&reportUnit=xxx&province=xxx&city=xxx&categoryCode=xxx&assetCategory=xxx&quantityMin=xxx&quantityMax=xxx&usedQuantityMin=xxx&usedQuantityMax=xxx&startUseDateStart=xxx&startUseDateEnd=xxx&inventoryUnit=xxx\n" + // 新增：网信基础资产联合查询
                        "   • *网信资产使用率分析: /api/asset/cyber/usage-rate/report-unit/{reportUnit}\n" +
                        "\n" +
                        "（3）数据内容产表接口：\n" +
                        "   • 数据资产详情: /api/asset/data/{id}\n" +
                        "   • 数据资产联合查询: /api/asset/data/combined-query?pageNum=1&pageSize=50&reportUnit=xxx&province=xxx&city=xxx&applicationField=xxx&developmentTool=xxx&quantityMin=xxx&quantityMax=xxx&updateCycle=xxx&updateMethod=xxx&inventoryUnit=xxx\n" + // 新增：数据内容资产联合查询
                        "   • *数据资产信息化程度（全部省份）: /api/asset/data/province/information-degree\n" +
                        "   • *数据资产国产化率（全部省份）: /api/asset/data/province/domestic-rate\n\n" +
                        "\n" +
                        "（4）单独要实现的额外查询接口（GET请求）：\n" +
                            " a)接口1:\n" +
                        "   • 三类资产数据量统计: /api/asset/statistics/count\n" +
                            " b)接口2: 按资产信息分类\n" +
                        "   • 软件应用资产表快速查询资产分类: /api/asset/software/category-query?categoryCode=xxx&assetCategory=xxx&pageNum=1&pageSize=50\n" +
                        "   • 网信基础资产资产快速查询资产分类: /api/asset/cyber/category-query?categoryCode=xxx&assetCategory=xxx&pageNum=1&pageSize=50\n" +
                        "   • 数据资产应用领域查询: /api/asset/data/field-query?applicationField=xxx&pageNum=1&pageSize=50\n" +
                            " c)接口3: 按上报单位查询\n" +
                        "   • 软件资产按上报单位查询: /api/asset/software/unit-assets?reportUnit=xxx&pageNum=1&pageSize=50\n" +
                        "   • 网信资产按上报单位查询: /api/asset/cyber/unit-assets?reportUnit=xxx&pageNum=1&pageSize=50\n" +
                        "   • 数据资产按上报单位查询: /api/asset/data/unit-assets?reportUnit=xxx&pageNum=1&pageSize=50\n" +
                        "   • 上报单位列表: /api/asset/report-units?tableType=software（可选参数：software/cyber/data）\n" +
                            " d)接口4: 上报单位对应的省份统计\n" +
                        "   • 各省份的单位统计-在各资产下: /api/asset/province/asset-tables\n" +
                        "   • 各省份的单位统计-在全资产下: /api/asset/province/report-units\n" +
                            " e）接口5：固定选项下拉菜单接口：\n" +
                        "   • 数据内容资产固定选项: /api/asset/data/fixed-options\n" +
                        "   • 软件应用资产固定选项: /api/asset/software/fixed-options\n" +
                        "   • 网信基础资产固定选项: /api/asset/cyber/fixed-options\n\n" +

                        // ============================== 1119 新增 ==============================
                            " f）接口6：省份资产统计接口（两个接口）：\n" +
                        "   • 1）按省份统计三类资产数量和百分比: /api/asset/statistics/province-asset-overview\n" +
                        "     作用：统计34个省份+\"未知\"的三类资产数量及占比\n" +
                        "     返回：总数量 + 各省份三类资产的数量和百分比\n" +

                        "   • 2）按省份和资产类型统计其资产分类细分: /api/asset/statistics/province-asset-detail?province=xx省&assetType=software\n" +
                        "     参数：province(必填), assetType(必填: software/cyber/data)\n" +
                        "     作用：统计指定省份下指定资产类型的各资产分类数量和占比\n" +
                        "     返回：省份+资产类型+总数+各分类统计\n" +


                        // ============================== 增改删接口（1118ok） ==============================
                        "➕ 新增接口（POST请求，JSON格式）：\n" +
                        "   • 新增软件资产: /api/asset/software\n" +
                        "   • 新增网信资产: /api/asset/cyber\n" +
                        "   • 新增数据资产: /api/asset/data\n\n" +

                        "✏️ 修改接口（PUT请求，JSON格式）：\n" +
                        "   • 修改软件资产: /api/asset/software\n" +
                        "   • 修改网信资产: /api/asset/cyber\n" +
                        "   • 修改数据资产: /api/asset/data\n\n" +

                        "🗑️ 删除接口（DELETE请求）：\n" +
                        "   • 删除软件资产: /api/asset/software/{id}\n" +
                        "   • 删除网信资产: /api/asset/cyber/{id}\n" +
                        "   • 删除数据资产: /api/asset/data/{id}\n\n" +

                        // ============================== 其他说明 ==============================
                        "💡 使用说明：\n" +
                        "   1. 所有CRUD接口返回统一格式：{code:200, message:\"成功\", data:...}\n" +
                        "   2. Excel导入支持.xlsx和.xls格式\n" +
                        "   3. 日期格式：YYYY-MM-DD（如：2025-10-09）\n" +
                        "   4. 金额字段支持小数，保留2位小数\n\n" +
                        "🔧 技术栈：\n" +
                        "   • 后端：Spring Boot 3.2.0 + MyBatis-Plus 3.5.4\n" +
                        "   • 数据库：MySQL 8.0\n" +
                        "   • Excel解析：EasyExcel 3.3.2\n" +
                        "   • 构建工具：Maven\n\n" +
                        "📞 如有问题，请联系系统管理员";

        return ResultVO.success(welcomeMessage, "系统首页加载成功");
    }

    // ============================== 三类资产数量统计接口 ==============================

    /**
     * 接口1：三类资产数据量统计
     * 访问路径：GET http://localhost:8080/api/asset/statistics/count
     * 作用：分别统计软件资产、网信资产、数据内容资产的数据量（行数）
     * 返回格式：{"code":200,"message":"成功","data":{"softwareCount":100,"cyberCount":50,"dataContentCount":80}}
     */
    @GetMapping("/statistics/count")
    public ResultVO<Map<String, Long>> getAssetCounts() {
        try {
            log.info("开始统计三类资产数据量...");

            Map<String, Long> counts = new HashMap<>();
            long softwareCount = softwareService.count();
            long cyberCount = cyberService.count();
            long dataContentCount = dataService.count();

            counts.put("softwareCount", softwareCount);
            counts.put("cyberCount", cyberCount);
            counts.put("dataContentCount", dataContentCount);

            log.info("资产统计完成 - 软件: {}, 网信: {}, 数据: {}",
                    softwareCount, cyberCount, dataContentCount);

            return ResultVO.success(counts, "获取资产数量统计成功");
        } catch (Exception e) {
            log.error("获取资产数量统计失败", e);
            return ResultVO.fail("统计失败：" + e.getMessage());
        }
    }

    // ============================== 新增：上报单位相关接口（接口3用） ==============================

    /**
     * 获取上报单位列表接口
     * 作用：为前端提供上报单位下拉菜单数据，让用户可以选择而不是手动输入
     * 前端用途：在查询界面提供下拉选择，避免输入错误
     */
    @GetMapping("/report-units")
    public ResultVO<Map<String, Object>> getAllReportUnits(
            @RequestParam(required = false) String tableType) {
        try {
            log.info("获取上报单位列表 - 表类型: {}", tableType);

            List<String> reportUnits;
            String message;

            if (tableType != null && !tableType.isEmpty()) {
                // 验证表类型参数是否合法
                if (!isValidTableType(tableType)) {
                    return ResultVO.fail("表类型参数不合法，必须是: software, cyber, data");
                }

                // 获取指定资产表的上报单位
                reportUnits = reportUnitService.getReportUnitsByTableType(tableType);
                message = String.format("获取%s资产表上报单位列表成功", getTableTypeChineseName(tableType));
            } else {
                // 获取所有上报单位
                reportUnits = reportUnitService.getAllReportUnitNames();
                message = "获取所有上报单位列表成功";
            }

            Map<String, Object> response = new HashMap<>();
            response.put("reportUnits", reportUnits);
            response.put("count", reportUnits.size());
            if (tableType != null) {
                response.put("tableType", tableType);
                response.put("tableTypeName", getTableTypeChineseName(tableType));
            }

            log.info("{} - 总数: {}", message, reportUnits.size());

            return ResultVO.success(response, message);
        } catch (Exception e) {
            log.error("获取上报单位列表失败", e);
            return ResultVO.fail("获取失败：" + e.getMessage());
        }
    }

    /**
     * 接口3：软件资产按上报单位查询
     * 作用：根据上报单位查询该单位名下的所有软件资产
     */
    @GetMapping("/software/unit-assets")
    public ResultVO<Map<String, Object>> getSoftwareByUnit(
            @RequestParam String reportUnit,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            log.info("软件资产按上报单位查询 - 上报单位: {}, 页码: {}, 页大小: {}",
                    reportUnit, pageNum, pageSize);

            // 先验证上报单位是否存在于软件资产表中
            if (!reportUnitService.validateReportUnitExists(reportUnit, "software")) {
                return ResultVO.fail("该上报单位在软件资产表中不存在");
            }

            pageSize = Math.min(pageSize, 50);
            Page<SoftwareAsset> pageInfo = new Page<>(pageNum, pageSize);
            Page<SoftwareAsset> result = softwareService.queryByReportUnit(pageInfo, reportUnit);

            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("pageNum", result.getCurrent());
            response.put("pageSize", result.getSize());
            response.put("totalPages", result.getPages());

            log.info("软件资产按上报单位查询成功 - 总数: {}, 当前页: {}, 总页数: {}",
                    result.getTotal(), result.getCurrent(), result.getPages());

            return ResultVO.success(response, "获取上报单位软件资产成功");
        } catch (Exception e) {
            log.error("获取上报单位软件资产失败", e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

    /**
     * 接口3：网信资产按上报单位查询
     * 作用：根据上报单位查询该单位名下的所有网信资产
     */
    @GetMapping("/cyber/unit-assets")
    public ResultVO<Map<String, Object>> getCyberByUnit(
            @RequestParam String reportUnit,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            log.info("网信资产按上报单位查询 - 上报单位: {}, 页码: {}, 页大小: {}",
                    reportUnit, pageNum, pageSize);

            // 先验证上报单位是否存在于网信资产表中
            if (!reportUnitService.validateReportUnitExists(reportUnit, "cyber")) {
                return ResultVO.fail("该上报单位在网信资产表中不存在");
            }

            pageSize = Math.min(pageSize, 50);
            Page<CyberAsset> pageInfo = new Page<>(pageNum, pageSize);
            Page<CyberAsset> result = cyberService.queryByReportUnit(pageInfo, reportUnit);

            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("pageNum", result.getCurrent());
            response.put("pageSize", result.getSize());
            response.put("totalPages", result.getPages());

            log.info("网信资产按上报单位查询成功 - 总数: {}, 当前页: {}, 总页数: {}",
                    result.getTotal(), result.getCurrent(), result.getPages());

            return ResultVO.success(response, "获取上报单位网信资产成功");
        } catch (Exception e) {
            log.error("获取上报单位网信资产失败", e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

    /**
     * 接口3：数据资产按上报单位查询
     * 作用：根据上报单位查询该单位名下的所有数据资产
     */
    @GetMapping("/data/unit-assets")
    public ResultVO<Map<String, Object>> getDataByUnit(
            @RequestParam String reportUnit,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            log.info("数据资产按上报单位查询 - 上报单位: {}, 页码: {}, 页大小: {}",
                    reportUnit, pageNum, pageSize);

            // 先验证上报单位是否存在于数据资产表中
            if (!reportUnitService.validateReportUnitExists(reportUnit, "data")) {
                return ResultVO.fail("该上报单位在数据资产表中不存在");
            }

            pageSize = Math.min(pageSize, 50);
            Page<DataContentAsset> pageInfo = new Page<>(pageNum, pageSize);
            Page<DataContentAsset> result = dataService.queryByReportUnit(pageInfo, reportUnit);

            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("pageNum", result.getCurrent());
            response.put("pageSize", result.getSize());
            response.put("totalPages", result.getPages());

            log.info("数据资产按上报单位查询成功 - 总数: {}, 当前页: {}, 总页数: {}",
                    result.getTotal(), result.getCurrent(), result.getPages());

            return ResultVO.success(response, "获取上报单位数据资产成功");
        } catch (Exception e) {
            log.error("获取上报单位数据资产失败", e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

// ============================== 新增：接口4 省份单位统计接口 ==============================

    /**
     * 接口4(a)：根据三个表各自的包含的上报单位的省份信息，统计各省中的数量
     * 访问路径：GET http://localhost:8080/api/asset/province/asset-tables
     * 作用：分别统计三个资产表中各省份的上报单位数量
     * 新逻辑说明：
     * - 由于软件资产表没有province列，所有三个表都通过关联report_unit表获取省份信息
     * - 确保数据来源的一致性，避免直接查询资产表的province字段（可能为空或不一致）
     * - 通过report_unit表的province字段获取准确的省份信息
     * 返回格式：
     * {
     *   "code": 200,
     *   "message": "获取各资产表省份统计成功",
     *   "data": {
     *     "softwareProvinceStats": [{"province": "北京", "count": 5}, ...],
     *     "cyberProvinceStats": [{"province": "北京", "count": 3}, ...],
     *     "dataProvinceStats": [{"province": "北京", "count": 4}, ...]
     *   }
     * }
     */
    @GetMapping("/province/asset-tables")
    public ResultVO<Map<String, Object>> getProvinceStatsFromAssetTables() {
        try {
            log.info("开始统计各资产表省份单位数量...");

            Map<String, Object> result = new HashMap<>();

            // 获取软件资产表的省份统计 - 新逻辑：通过关联report_unit表获取省份
            List<Map<String, Object>> softwareStats = softwareService.getProvinceUnitStats();
            result.put("softwareProvinceStats", softwareStats);

            // 获取网信资产表的省份统计 - 新逻辑：通过关联report_unit表获取省份
            List<Map<String, Object>> cyberStats = cyberService.getProvinceUnitStats();
            result.put("cyberProvinceStats", cyberStats);

            // 获取数据资产表的省份统计 - 新逻辑：通过关联report_unit表获取省份
            List<Map<String, Object>> dataStats = dataService.getProvinceUnitStats();
            result.put("dataProvinceStats", dataStats);

            log.info("各资产表省份统计完成");

            return ResultVO.success(result, "获取各资产表省份统计成功");
        } catch (Exception e) {
            log.error("获取省份统计失败", e);
            return ResultVO.fail("统计失败：" + e.getMessage());
        }
    }

    /**
     * 接口4(b)：根据上报单位表的省份信息，统计各省中的数量
     * 访问路径：GET http://localhost:8080/api/asset/province/report-units
     * 作用：统计上报单位表中各省份的单位数量（只统计有数据的单位）
     * 新逻辑说明：
     * - 直接从report_unit表统计，不涉及关联查询
     * - 只统计在至少一个资产表中有数据的单位（source_table_xxx_asset至少有一个为1）
     * - 这反映了各省份有数据的单位总数（去重后的结果）
     * 与接口4(a)的区别：
     * - 4(a)：分别统计三个资产表中各省份的单位分布
     * - 4(b)：统计各省份有数据的单位总数（一个单位在多个资产表有数据也只统计一次）
     * 返回格式：
     * {
     *   "code": 200,
     *   "message": "获取上报单位省份统计成功",
     *   "data": [
     *     {"province": "北京", "count": 8},
     *     {"province": "上海", "count": 6},
     *     ...
     *   ]
     * }
     */
    @GetMapping("/province/report-units")
    public ResultVO<List<Map<String, Object>>> getProvinceStatsFromReportUnits() {
        try {
            log.info("开始统计上报单位表省份单位数量...");

            List<Map<String, Object>> result = reportUnitService.getProvinceUnitStats();

            log.info("上报单位表省份统计完成 - 总数: {}", result.size());

            return ResultVO.success(result, "获取上报单位省份统计成功");
        } catch (Exception e) {
            log.error("获取上报单位省份统计失败", e);
            return ResultVO.fail("统计失败：" + e.getMessage());
        }
    }

// ============================== 固定选项下拉菜单接口（接口5） ==============================

/**
 * 接口5：获取三类资产的固定选项内容（为前端提供下拉菜单数据）
 */

    /**
     * 5(a) 数据内容资产表的固定选项
     * 访问路径：GET /api/asset/data/fixed-options
     * 作用：为前端提供数据内容资产表的下拉菜单数据
     * 返回：分类编码与资产分类映射、应用领域、开发工具、更新周期、更新方式等固定选项（1115修正）
     */
    @GetMapping("/data/fixed-options")
    public ResultVO<Map<String, Object>> getDataContentFixedOptions() {
        try {
            log.info("获取数据内容资产固定选项...");

            Map<String, Object> options = new HashMap<>();

            // 分类编码与资产分类映射（从CategoryMapUtils获取）
            Map<String, String> categoryMap = CategoryMapUtils.initDataCategoryMap();

            // 按指定顺序重新组织分类编码和资产分类
            List<String> orderedCategoryCodes = new ArrayList<>();
            List<String> orderedAssetCategories = new ArrayList<>();

            // 数据内容资产的分类（只有一个分类）
            orderedCategoryCodes.add("006004003");
            orderedAssetCategories.add("数据内容资产");

            // 重建有序的映射
            Map<String, String> orderedCategoryMap = new LinkedHashMap<>();
            for (int i = 0; i < orderedCategoryCodes.size(); i++) {
                orderedCategoryMap.put(orderedCategoryCodes.get(i), orderedAssetCategories.get(i));
            }

            options.put("categoryMapping", orderedCategoryMap);
            options.put("categoryCodes", orderedCategoryCodes);
            options.put("assetCategories", orderedAssetCategories);

            // 应用领域固定选项 - 按指定顺序
            List<String> applicationField = new ArrayList<>();
            applicationField.add("后勤保障");
            applicationField.add("建设规划");
            applicationField.add("日常办公");
            applicationField.add("战备管理");
            applicationField.add("政治工作");
            applicationField.add("装备保障");
            applicationField.add("作战指挥");
            applicationField.add("其他");
            options.put("applicationField", applicationField);

            // 开发工具固定选项 - 按指定顺序
            List<String> developmentTool = new ArrayList<>();
            developmentTool.add("Oracle");
            developmentTool.add("MySql");
            developmentTool.add("SQL Server");
            developmentTool.add("HDFS");
            developmentTool.add("达梦");
            developmentTool.add("高斯");
            developmentTool.add("南大通用");
            developmentTool.add("人大金仓");
            developmentTool.add("神州通用");
            developmentTool.add("其他");
            options.put("developmentTool", developmentTool);

            // 更新周期固定选项 - 按指定顺序
            List<String> updateCycle = new ArrayList<>();
            updateCycle.add("实时");
            updateCycle.add("每天");
            updateCycle.add("每月");
            updateCycle.add("每季度");
            updateCycle.add("每半年");
            updateCycle.add("每年");
            updateCycle.add("不更新");
            updateCycle.add("其他");
            options.put("updateCycle", updateCycle);

            // 更新方式固定选项 - 按指定顺序
            List<String> updateMethod = new ArrayList<>();
            updateMethod.add("自动采集");
            updateMethod.add("在线填报");
            updateMethod.add("离线填报");
            updateMethod.add("商业购置");
            updateMethod.add("上级请领");
            updateMethod.add("其他");
            options.put("updateMethod", updateMethod);

            log.info("数据内容资产固定选项获取成功");
            return ResultVO.success(options, "获取数据内容资产固定选项成功");
        } catch (Exception e) {
            log.error("获取数据内容资产固定选项失败", e);
            return ResultVO.fail("获取固定选项失败：" + e.getMessage());
        }
    }

    /**
     * 5(b) 软件应用资产表的固定选项
     * 访问路径：GET /api/asset/software/fixed-options
     * 作用：为前端提供软件应用资产表的下拉菜单数据
     * 返回：分类编码与资产分类映射、取得方式、部署范围、服务状态等固定选项 （1115修正）
     */
    @GetMapping("/software/fixed-options")
    public ResultVO<Map<String, Object>> getSoftwareFixedOptions() {
        try {
            log.info("获取软件应用资产固定选项...");

            Map<String, Object> options = new HashMap<>();

            // 分类编码与资产分类映射（从CategoryMapUtils获取）
            Map<String, String> categoryMap = CategoryMapUtils.initSoftwareCategoryMap();

            // 按指定顺序重新组织分类编码和资产分类
            List<String> orderedCategoryCodes = new ArrayList<>();
            List<String> orderedAssetCategories = new ArrayList<>();

            // 按照你希望的顺序添加分类
            orderedCategoryCodes.add("006004002001001");
            orderedAssetCategories.add("操作系统");
            orderedCategoryCodes.add("006004002001002");
            orderedAssetCategories.add("数据库系统");
            orderedCategoryCodes.add("006004002001003");
            orderedAssetCategories.add("中间件");
            orderedCategoryCodes.add("006004002001004");
            orderedAssetCategories.add("软件开发环境");
            orderedCategoryCodes.add("006004002002001");
            orderedAssetCategories.add("网络通信软件");
            orderedCategoryCodes.add("006004002002002");
            orderedAssetCategories.add("文档处理软件");
            orderedCategoryCodes.add("006004002002003");
            orderedAssetCategories.add("图形图像软件");
            orderedCategoryCodes.add("006004002002004");
            orderedAssetCategories.add("数据处理软件");
            orderedCategoryCodes.add("006004002002005");
            orderedAssetCategories.add("模型算法软件");
            orderedCategoryCodes.add("006004002002006");
            orderedAssetCategories.add("地理信息系统");
            orderedCategoryCodes.add("006004002002007");
            orderedAssetCategories.add("移动应用软件");
            orderedCategoryCodes.add("006004002002008");
            orderedAssetCategories.add("安全防护软件");
            orderedCategoryCodes.add("006004002002009");
            orderedAssetCategories.add("设备管理软件");
            orderedCategoryCodes.add("006004002003001");
            orderedAssetCategories.add("作战指挥软件");
            orderedCategoryCodes.add("006004002003002");
            orderedAssetCategories.add("业务管理软件");
            orderedCategoryCodes.add("006004002003003");
            orderedAssetCategories.add("日常办公软件");

            // 重建有序的映射
            Map<String, String> orderedCategoryMap = new LinkedHashMap<>();
            for (int i = 0; i < orderedCategoryCodes.size(); i++) {
                orderedCategoryMap.put(orderedCategoryCodes.get(i), orderedAssetCategories.get(i));
            }

            options.put("categoryMapping", orderedCategoryMap);
            options.put("categoryCodes", orderedCategoryCodes);
            options.put("assetCategories", orderedAssetCategories);

            // 取得方式固定选项 - 按指定顺序
            List<String> acquisitionMethod = new ArrayList<>();
            acquisitionMethod.add("购置");
            acquisitionMethod.add("自主开发");
            acquisitionMethod.add("合作开发");
            acquisitionMethod.add("其他");
            options.put("acquisitionMethod", acquisitionMethod);

            // 部署范围固定选项 - 按指定顺序
            List<String> deploymentScope = new ArrayList<>();
            deploymentScope.add("军以下");
            deploymentScope.add("全军");
            deploymentScope.add("战区");
            deploymentScope.add("军级单位内部");
            deploymentScope.add("军种");
            options.put("deploymentScope", deploymentScope);

//            // 部署形式 - 按指定顺序 （非固定）
//            List<String> deploymentForm = new ArrayList<>();
//            deploymentForm.add("本地部署");
//            deploymentForm.add("异地部署");
//            deploymentForm.add("联合部署");
//            deploymentForm.add("其他");
//            options.put("deploymentForm", deploymentForm);

//            // 承载网络 - 按指定顺序（非固定）
//            List<String> carryingNetwork = new ArrayList<>();
//            carryingNetwork.add("安装");
//            carryingNetwork.add("公开");
//            carryingNetwork.add("本级");
//            carryingNetwork.add("安装到办公电脑上");
//            carryingNetwork.add("单机部署");
//            carryingNetwork.add("登记使用");
//            carryingNetwork.add("二级网");
//            carryingNetwork.add("手机app软件");
//            carryingNetwork.add("所有办公电脑");
//            options.put("carryingNetwork", carryingNetwork);

            // 服务状态固定选项 - 按指定顺序
            List<String> serviceStatus = new ArrayList<>();
            serviceStatus.add("在用");
            serviceStatus.add("闲置");
            serviceStatus.add("报废");
            serviceStatus.add("封闭");
            options.put("serviceStatus", serviceStatus);

            log.info("软件应用资产固定选项获取成功");
            return ResultVO.success(options, "获取软件应用资产固定选项成功");
        } catch (Exception e) {
            log.error("获取软件应用资产固定选项失败", e);
            return ResultVO.fail("获取固定选项失败：" + e.getMessage());
        }
    }

    /**
     * 5(c) 网信基础资产表的固定选项
     * 访问路径：GET /api/asset/cyber/fixed-options
     * 作用：为前端提供网信基础资产表的下拉菜单数据
     * 返回：分类编码与资产分类映射等固定选项 （1115修正）
     */
    @GetMapping("/cyber/fixed-options")
    public ResultVO<Map<String, Object>> getCyberFixedOptions() {
        try {
            log.info("获取网信基础资产固定选项...");

            Map<String, Object> options = new HashMap<>();

            // 分类编码与资产分类映射（从CategoryMapUtils获取）
            Map<String, String> categoryMap = CategoryMapUtils.initCyberCategoryMap();

            // 按指定顺序重新组织分类编码和资产分类
            List<String> orderedCategoryCodes = new ArrayList<>();
            List<String> orderedAssetCategories = new ArrayList<>();

            // 按照你希望的顺序添加分类
            orderedCategoryCodes.add("006004001001");
            orderedAssetCategories.add("自动电话号码");
            orderedCategoryCodes.add("006004001002");
            orderedAssetCategories.add("人工电话号码");
            orderedCategoryCodes.add("006004001003");
            orderedAssetCategories.add("保密电话号码");
            orderedCategoryCodes.add("006004001004");
            orderedAssetCategories.add("移动手机号码");
            orderedCategoryCodes.add("006004001005");
            orderedAssetCategories.add("有线信道");
            orderedCategoryCodes.add("006004001006");
            orderedAssetCategories.add("光缆纤芯");
            orderedCategoryCodes.add("006004001007");
            orderedAssetCategories.add("骨干网节点互联网络地址");
            orderedCategoryCodes.add("006004001008");
            orderedAssetCategories.add("骨干网节点设备管理地址");
            orderedCategoryCodes.add("006004001009");
            orderedAssetCategories.add("网络地址");
            orderedCategoryCodes.add("006004001010");
            orderedAssetCategories.add("文电名录");
            orderedCategoryCodes.add("006004001011");
            orderedAssetCategories.add("军事网络域名");
            orderedCategoryCodes.add("006004001012");
            orderedAssetCategories.add("互联网域名");
            orderedCategoryCodes.add("006004001014");
            orderedAssetCategories.add("无线电报代号");
            orderedCategoryCodes.add("006004001015");
            orderedAssetCategories.add("电磁频谱");
            orderedCategoryCodes.add("006004001016");
            orderedAssetCategories.add("数据中心计算资产");
            orderedCategoryCodes.add("006004001017");
            orderedAssetCategories.add("数据中心存储资产");
            orderedCategoryCodes.add("006004001999");
            orderedAssetCategories.add("其他网信基础资产");

            // 重建有序的映射
            Map<String, String> orderedCategoryMap = new LinkedHashMap<>();
            for (int i = 0; i < orderedCategoryCodes.size(); i++) {
                orderedCategoryMap.put(orderedCategoryCodes.get(i), orderedAssetCategories.get(i));
            }

            options.put("categoryMapping", orderedCategoryMap);
            options.put("categoryCodes", orderedCategoryCodes);
            options.put("assetCategories", orderedAssetCategories);

            log.info("网信基础资产固定选项获取成功");
            return ResultVO.success(options, "获取网信基础资产固定选项成功");
        } catch (Exception e) {
            log.error("获取网信基础资产固定选项失败", e);
            return ResultVO.fail("获取固定选项失败：" + e.getMessage());
        }
    }

    // ============================== 软件资产查询 ==============================

    @GetMapping("/software/{id}")
    public ResultVO<SoftwareAsset> getSoftware(@PathVariable String id) {
        try {
            SoftwareAsset asset = softwareService.getById(id);
            return ResultVO.success(asset, "查询软件资产详情成功");
        } catch (RuntimeException e) {
            log.error("查询软件资产失败，ID：{}", id, e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

// ======================  修改：软件资产联合查询接口（支持实有数量范围查询 + 盘点单位筛选） ======================
    /**
     * 软件资产联合查询接口（支持实有数量范围查询 + 盘点单位筛选）
     * 访问路径：GET /api/asset/software/combined-query?pageNum=1&pageSize=50&reportUnit=xxx&categoryCode=xxx&assetCategory=xxx&acquisitionMethod=xxx&deploymentScope=xxx&deploymentForm=xxx&bearingNetwork=xxx&quantityMin=xxx&quantityMax=xxx&serviceStatus=xxx&startUseDateStart=xxx&startUseDateEnd=xxx&inventoryUnit=xxx

     * 作用：支持多条件自由组合查询软件资产，返回分页结果和总数
     * 特点：
     * - 实有数量支持范围查询（从0往上）
     * - 支持盘点单位筛选
     * - 所有参数均为可选，可以自由组合
     * - 默认每页显示50条数据，适合大数据量场景
     *
     * @param pageNum 当前页码，从1开始，默认值为1
     * @param pageSize 每页显示条数，默认值为50
     * @param reportUnit 上报单位（可选筛选条件）
     * @param categoryCode 分类编码（可选筛选条件，与assetCategory绑定）
     * @param assetCategory 资产分类（可选筛选条件，与categoryCode绑定）
     * @param acquisitionMethod 取得方式（可选筛选条件）
     * @param deploymentScope 部署范围（可选筛选条件）
     * @param deploymentForm 部署形式（可选筛选条件）
     * @param bearingNetwork 承载网络（可选筛选条件）
     * @param quantityMin 实有数量最小值（可选筛选条件，>=0）
     * @param quantityMax 实有数量最大值（可选筛选条件，>=quantityMin）
     * @param serviceStatus 服务状态（可选筛选条件）
     * @param startUseDateStart 投入使用时间范围开始（可选筛选条件，格式：YYYY-MM-DD）
     * @param startUseDateEnd 投入使用时间范围结束（可选筛选条件，格式：YYYY-MM-DD）
     * @param inventoryUnit 盘点单位（可选筛选条件）
     * @return 包含分页信息的查询结果
     */
    @GetMapping("/software/combined-query")
    public ResultVO<Object> getSoftwareCombinedQuery(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String reportUnit,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String assetCategory,
            @RequestParam(required = false) String acquisitionMethod,
            @RequestParam(required = false) String deploymentScope,
            @RequestParam(required = false) String deploymentForm,
            @RequestParam(required = false) String bearingNetwork,
            @RequestParam(required = false) Integer quantityMin,
            @RequestParam(required = false) Integer quantityMax,
            @RequestParam(required = false) String serviceStatus,
            @RequestParam(required = false) String startUseDateStart,
            @RequestParam(required = false) String startUseDateEnd,
            @RequestParam(required = false) String inventoryUnit) {
        try {
            // 调用Service层联合查询方法，传入所有筛选条件
            Object queryResult = softwareService.combinedQuery(
                    pageNum, pageSize, reportUnit, categoryCode, assetCategory,
                    acquisitionMethod, deploymentScope, deploymentForm, bearingNetwork,
                    quantityMin, quantityMax, serviceStatus, startUseDateStart, startUseDateEnd,
                    inventoryUnit
            );
            return ResultVO.success(queryResult, "软件资产联合查询成功");
        } catch (Exception e) {
            log.error("软件资产联合查询失败，参数：pageNum={}, pageSize={}, reportUnit={}, categoryCode={}, assetCategory={}",
                    pageNum, pageSize, reportUnit, categoryCode, assetCategory, e);
            return ResultVO.fail("联合查询失败：" + e.getMessage());
        }
    }

    // ============================== 新增：软件资产额外查询接口 ==============================
    /**
     * 接口2(1)：软件资产按分类编码或资产分类查询
     * 访问路径：GET http://localhost:8080/api/asset/software/category-query
     * 参数：categoryCode(可选), assetCategory(可选), pageNum(可选,默认1), pageSize(可选,默认50,最大50)
     * 作用：根据分类编码或资产分类筛选软件资产，支持分页
     */
    @GetMapping("/software/category-query")
    public ResultVO<Map<String, Object>> querySoftwareByCategory(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String assetCategory,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            log.info("软件资产分类查询 - 分类编码: {}, 资产分类: {}, 页码: {}, 页大小: {}",
                    categoryCode, assetCategory, pageNum, pageSize);

            // 限制每页最大50条
            pageSize = Math.min(pageSize, 50);
            Page<SoftwareAsset> pageInfo = new Page<>(pageNum, pageSize);
            Page<SoftwareAsset> result = softwareService.queryByCategory(pageInfo, categoryCode, assetCategory);

            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("pageNum", result.getCurrent());
            response.put("pageSize", result.getSize());
            response.put("totalPages", result.getPages());

            log.info("软件资产分类查询成功 - 总数: {}, 当前页: {}, 总页数: {}",
                    result.getTotal(), result.getCurrent(), result.getPages());

            return ResultVO.success(response, "软件资产分类查询成功");
        } catch (Exception e) {
            log.error("软件资产分类查询失败", e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

    @GetMapping("/software/statistics")
    public ResultVO<List<SoftwareAssetStatisticVO>> statisticSoftware() {
        try {
            List<SoftwareAssetStatisticVO> statistics = softwareService.statisticsByReportUnit();
            return ResultVO.success(statistics, "查询软件资产统计成功（共" + statistics.size() + "条）");
        } catch (Exception e) {
            log.error("统计软件资产取得方式与服务状态失败", e);
            return ResultVO.fail("统计失败：" + e.getMessage());
        }
    }


    /**
     * 新增软件应用资产
     * 访问路径：POST /api/asset/software
     * 功能：接收前端JSON数据，调用Service层新增软件资产
     * 特点：软件资产表没有省市字段，所有省市信息通过上报单位表间接管理
     * 同步逻辑：自动推导省市信息并同步到上报单位表
     *
     * @param asset 软件资产对象（JSON格式）
     * @return 操作结果
     */
    @PostMapping("/software")
    public ResultVO<Void> addSoftware(@RequestBody SoftwareAsset asset) {
        try {
            log.info("新增软件资产 - 资产ID: {}, 资产名称: {}", asset.getId(), asset.getAssetName());

            // 调用Service层新增方法（包含完整的数据校验和同步逻辑）
            softwareService.add(asset);

            log.info("新增软件资产成功 - 资产ID: {}", asset.getId());
            return ResultVO.success("新增软件资产成功，ID：" + asset.getId());
        } catch (RuntimeException e) {
            log.error("新增软件资产失败，ID：{}", asset.getId(), e);
            return ResultVO.fail("新增失败：" + e.getMessage());
        }
    }

    @PutMapping("/software")
    public ResultVO<Void> updateSoftware(@RequestBody SoftwareAsset asset) {
        try {
            softwareService.update(asset);
            return ResultVO.success("修改软件资产成功，ID：" + asset.getId());
        } catch (RuntimeException e) {
            log.error("修改软件资产失败，ID：{}", asset.getId(), e);
            return ResultVO.fail("修改失败：" + e.getMessage());
        }
    }

    // 软件资产删除
    @DeleteMapping("/software/{id}")
    public ResultVO<Void> deleteSoftware(@PathVariable String id) {
        try {
            softwareService.remove(id);
            return ResultVO.success("删除软件资产成功，ID：" + id);
        } catch (RuntimeException e) {
            log.error("删除软件资产失败，ID：{}", id, e);
            return ResultVO.fail("删除失败：" + e.getMessage());
        }
    }

    // ============================== 网信资产查询 ==============================
    @GetMapping("/cyber/{id}")
    public ResultVO<CyberAsset> getCyber(@PathVariable String id) {
        try {
            CyberAsset asset = cyberService.getById(id);
            return ResultVO.success(asset, "查询网信资产详情成功");
        } catch (RuntimeException e) {
            log.error("查询网信资产失败，ID：{}", id, e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }
// ====================== 新增：网信基础资产联合查询接口 ======================
    /**
     * 网信基础资产联合查询接口
     * 访问路径：GET /api/asset/cyber/combined-query?pageNum=1&pageSize=50&reportUnit=xxx&province=xxx&city=xxx&categoryCode=xxx&assetCategory=xxx&quantity=xxx&usedQuantity=xxx&startUseDateStart=xxx&startUseDateEnd=xxx

     * 作用：支持多条件自由组合查询网信基础资产，返回分页结果和总数
     * 适用场景：前端需要根据多个条件筛选网信基础资产，如按上报单位、省份、分类编码等组合查询

     * 特点：
     * - 所有参数均为可选，可以自由组合
     * - 默认每页显示50条数据，适合大数据量场景
     * - 返回完整的分页信息，包括总数据量
     * - 支持分类编码和资产分类的绑定查询
     * - 支持时间范围查询
     *
     * @param pageNum 当前页码，从1开始，默认值为1
     * @param pageSize 每页显示条数，默认值为50（针对3万条数据优化）
     * @param reportUnit 上报单位（可选筛选条件）
     * @param province 省份（可选筛选条件）
     * @param city 城市（可选筛选条件）
     * @param categoryCode 分类编码（可选筛选条件，与assetCategory绑定）
     * @param assetCategory 资产分类（可选筛选条件，与categoryCode绑定）
     * @param quantityMin 实有数量最小值（可选筛选条件，>=0）
     * @param quantityMax 实有数量最大值（可选筛选条件，>=quantityMin）
     * @param usedQuantityMin 已用数量最小值（可选筛选条件，>=0）
     * @param usedQuantityMax 已用数量最大值（可选筛选条件，>=usedQuantityMin）
     * @param startUseDateStart 投入使用时间范围开始（可选筛选条件，格式：YYYY-MM-DD）
     * @param startUseDateEnd 投入使用时间范围结束（可选筛选条件，格式：YYYY-MM-DD）
     * @param inventoryUnit 盘点单位（可选筛选条件）
     * @return 包含分页信息的查询结果，包括数据列表、总条数、总页数、当前页等信息
     */
    @GetMapping("/cyber/combined-query")
    public ResultVO<Object> getCyberCombinedQuery(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String reportUnit,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String assetCategory,
            @RequestParam(required = false) Integer quantityMin,
            @RequestParam(required = false) Integer quantityMax,
            @RequestParam(required = false) Integer usedQuantityMin,
            @RequestParam(required = false) Integer usedQuantityMax,
            @RequestParam(required = false) String startUseDateStart,
            @RequestParam(required = false) String startUseDateEnd,
            @RequestParam(required = false) String inventoryUnit) {
        try {
            // 调用Service层联合查询方法，传入所有筛选条件
            Object queryResult = cyberService.combinedQuery(
                    pageNum, pageSize, reportUnit, province, city, categoryCode,
                    assetCategory, quantityMin, quantityMax, usedQuantityMin, usedQuantityMax,
                    startUseDateStart, startUseDateEnd, inventoryUnit
            );
            return ResultVO.success(queryResult, "网信基础资产联合查询成功");
        } catch (Exception e) {
            log.error("网信基础资产联合查询失败，参数：pageNum={}, pageSize={}, reportUnit={}, province={}, city={}",
                    pageNum, pageSize, reportUnit, province, city, e);
            return ResultVO.fail("联合查询失败：" + e.getMessage());
        }
    }

   // ====================== 新增：网信基础资产额外查询接口 ======================
    /**
     * 接口2(1)：网信基础资产按分类编码或资产分类查询
     * 访问路径：GET http://localhost:8080/api/asset/cyber/category-query
     * 参数：categoryCode(可选), assetCategory(可选), pageNum(可选,默认1), pageSize(可选,默认50,最大50)
     * 作用：根据分类编码或资产分类筛选网信基础资产，支持分页
     */
    @GetMapping("/cyber/category-query")
    public ResultVO<Map<String, Object>> queryCyberByCategory(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String assetCategory,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            log.info("网信资产分类查询 - 分类编码: {}, 资产分类: {}, 页码: {}, 页大小: {}",
                    categoryCode, assetCategory, pageNum, pageSize);

            pageSize = Math.min(pageSize, 50);
            Page<CyberAsset> pageInfo = new Page<>(pageNum, pageSize);
            Page<CyberAsset> result = cyberService.queryByCategory(pageInfo, categoryCode, assetCategory);

            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("pageNum", result.getCurrent());
            response.put("pageSize", result.getSize());
            response.put("totalPages", result.getPages());

            log.info("网信资产分类查询成功 - 总数: {}, 当前页: {}, 总页数: {}",
                    result.getTotal(), result.getCurrent(), result.getPages());

            return ResultVO.success(response, "网信资产分类查询成功");
        } catch (Exception e) {
            log.error("网信资产分类查询失败", e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

    /**
     * 新增网信基础资产
     * 访问路径：POST /api/asset/cyber
     * 功能：接收前端JSON数据，调用Service层新增网信资产
     * 特点：网信资产表有省市字段，需要同时维护自身字段和上报单位表
     * 同步逻辑：自动填充省市信息，确保上报单位表状态和省市信息准确
     *
     * @param asset 网信资产对象（JSON格式）
     * @return 操作结果
     */
    @PostMapping("/cyber")
    public ResultVO<Void> addCyber(@RequestBody CyberAsset asset) {
        try {
            log.info("新增网信资产 - 资产ID: {}, 资产名称: {}", asset.getId(), asset.getAssetName());

            // 调用Service层新增方法（包含完整的数据校验和同步逻辑）
            cyberService.add(asset);

            log.info("新增网信资产成功 - 资产ID: {}", asset.getId());
            return ResultVO.success("新增网信资产成功，ID：" + asset.getId());
        } catch (RuntimeException e) {
            log.error("新增网信资产失败，ID：{}", asset.getId(), e);
            return ResultVO.fail("新增失败：" + e.getMessage());
        }
    }

    @PutMapping("/cyber")
    public ResultVO<Void> updateCyber(@RequestBody CyberAsset asset) {
        try {
            cyberService.update(asset);
            return ResultVO.success("修改网信资产成功，ID：" + asset.getId());
        } catch (RuntimeException e) {
            log.error("修改网信资产失败，ID：{}", asset.getId(), e);
            return ResultVO.fail("修改失败：" + e.getMessage());
        }
    }

    // 网信资产删除
    @DeleteMapping("/cyber/{id}")
    public ResultVO<Void> deleteCyber(@PathVariable String id) {
        try {
            cyberService.remove(id);
            return ResultVO.success("删除网信资产成功，ID：" + id);
        } catch (RuntimeException e) {
            log.error("删除网信资产失败，ID：{}", id, e);
            return ResultVO.fail("删除失败：" + e.getMessage());
        }
    }

    // ============================== 数据内容资产CRUD ==============================

    @GetMapping("/data/{id}")
    public ResultVO<DataContentAsset> getData(@PathVariable String id) {
        try {
            DataContentAsset asset = dataService.getById(id);
            return ResultVO.success(asset, "查询数据资产详情成功");
        } catch (RuntimeException e) {
            log.error("查询数据资产失败，ID：{}", id, e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

// ====================== 新增：数据内容资产联合查询接口 ======================
    /**
     * 数据内容资产联合查询接口
     * 访问路径：GET /api/asset/data/combined-query?pageNum=1&pageSize=50&reportUnit=xxx&province=xxx&city=xxx&applicationField=xxx&developmentTool=xxx&quantity=xxx&updateCycle=xxx&updateMethod=xxx&inventoryUnit=xxx

     * 作用：支持多条件自由组合查询数据内容资产，返回分页结果和总数
     * 适用场景：前端需要根据多个条件筛选数据内容资产，如按上报单位、省份、应用领域等组合查询

     * 特点：
     * - 所有参数均为可选，可以自由组合
     * - 默认每页显示50条数据，适合大数据量场景
     * - 返回完整的分页信息，包括总数据量
     * - 支持应用领域、开发工具等固定选项的筛选
     *
     * @param pageNum 当前页码，从1开始，默认值为1
     * @param pageSize 每页显示条数，默认值为50（针对3万条数据优化）
     * @param reportUnit 上报单位（可选筛选条件）
     * @param province 省份（可选筛选条件）
     * @param city 城市（可选筛选条件）
     * @param applicationField 应用领域（可选筛选条件，固定选项：后勤保障、建设规划、其他、日常办公、战备管理、政治工作、装备保障、作战指挥）
     * @param developmentTool 开发工具（可选筛选条件，固定选项：Oracle、HDFS、MySql、SQL Server、达梦、高斯、南大通用、其他、人大金仓、神州通用）
     * @param quantityMin 实有数量最小值（可选筛选条件，>=0）
     * @param quantityMax 实有数量最大值（可选筛选条件，>=quantityMin）
     * @param updateCycle 更新周期（可选筛选条件，固定选项：每月、每年、不更新、每半年、每季度、每天、其他、实时）
     * @param updateMethod 更新方式（可选筛选条件，固定选项：在线填报、离线填报、其他、商业购置、上级请领、自动采集）
     * @param inventoryUnit 盘点单位（可选筛选条件）
     * @return 包含分页信息的查询结果，包括数据列表、总条数、总页数、当前页等信息
     */
    @GetMapping("/data/combined-query")
    public ResultVO<Object> getDataCombinedQuery(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String reportUnit,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String applicationField,
            @RequestParam(required = false) String developmentTool,
            @RequestParam(required = false) Integer quantityMin,
            @RequestParam(required = false) Integer quantityMax,
            @RequestParam(required = false) String updateCycle,
            @RequestParam(required = false) String updateMethod,
            @RequestParam(required = false) String inventoryUnit) {
        try {
            // 调用Service层联合查询方法，传入所有筛选条件
            Object queryResult = dataService.combinedQuery(
                    pageNum, pageSize, reportUnit, province, city, applicationField,
                    developmentTool, quantityMin, quantityMax, updateCycle, updateMethod, inventoryUnit
            );
            return ResultVO.success(queryResult, "数据内容资产联合查询成功");
        } catch (Exception e) {
            log.error("数据内容资产联合查询失败，参数：pageNum={}, pageSize={}, reportUnit={}, province={}, city={}",
                    pageNum, pageSize, reportUnit, province, city, e);
            return ResultVO.fail("联合查询失败：" + e.getMessage());
        }
    }

    // ====================== 新增：数据内容资产额外查询接口 ======================
    /**
     * 接口2(2)：数据内容资产按应用领域查询
     * 访问路径：GET http://localhost:8080/api/asset/data/field-query
     * 参数：applicationField(必填), pageNum(可选,默认1), pageSize(可选,默认50,最大50)
     * 作用：根据应用领域筛选数据内容资产，支持分页
     */
    @GetMapping("/data/field-query")
    public ResultVO<Map<String, Object>> queryDataByField(
            @RequestParam(required = false) String applicationField,  // 改为可选
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            log.info("数据资产应用领域查询 - 应用领域: {}, 页码: {}, 页大小: {}",
                    applicationField, pageNum, pageSize);

            pageSize = Math.min(pageSize, 50);
            Page<DataContentAsset> pageInfo = new Page<>(pageNum, pageSize);
            Page<DataContentAsset> result = dataService.queryByApplicationField(pageInfo, applicationField);

            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("pageNum", result.getCurrent());
            response.put("pageSize", result.getSize());
            response.put("totalPages", result.getPages());

            log.info("数据资产应用领域查询成功 - 总数: {}, 当前页: {}, 总页数: {}",
                    result.getTotal(), result.getCurrent(), result.getPages());

            return ResultVO.success(response, "数据资产应用领域查询成功");
        } catch (Exception e) {
            log.error("数据资产应用领域查询失败", e);
            return ResultVO.fail("查询失败：" + e.getMessage());
        }
    }

    @GetMapping("/data/province/information-degree")
    public ResultVO<List<ProvinceMetricVO>> calculateInformationDegree() {
        try {
            List<ProvinceMetricVO> metrics = buildProvinceMetrics(dataService::calculateProvinceInformationDegree);
            return ResultVO.success(metrics, "各省份信息化程度计算成功");
        } catch (RuntimeException e) {
            log.error("各省份信息化程度批量计算失败", e);
            return ResultVO.fail("计算失败：" + e.getMessage());
        }
    }

    @GetMapping("/data/province/domestic-rate")
    public ResultVO<List<ProvinceMetricVO>> calculateDomesticRate() {
        try {
            List<ProvinceMetricVO> metrics = buildProvinceMetrics(dataService::calculateProvinceDomesticRate);
            return ResultVO.success(metrics, "各省份国产化率计算成功");
        } catch (RuntimeException e) {
            log.error("各省份国产化率批量计算失败", e);
            return ResultVO.fail("计算失败：" + e.getMessage());
        }
    }

    private List<ProvinceMetricVO> buildProvinceMetrics(Function<String, BigDecimal> calculator) {
        List<Province> provinces = provinceMapper.selectAll();
        if (Objects.isNull(provinces) || provinces.isEmpty()) {
            log.warn("省份表未查询到数据，返回空列表");
            return Collections.emptyList();
        }

        List<ProvinceMetricVO> metrics = new ArrayList<>(provinces.size());
        for (Province province : provinces) {
            if (province == null || province.getName() == null) {
                continue;
            }
            BigDecimal value = calculator.apply(province.getName());
            metrics.add(new ProvinceMetricVO(province.getCode(), province.getName(), value));
        }
        return metrics;
    }

    /**
     * 新增数据内容资产
     * 访问路径：POST /api/asset/data
     * 功能：接收前端JSON数据，调用Service层新增数据资产
     * 特点：数据资产表有省市字段，需要同时维护自身字段和上报单位表
     * 同步逻辑：自动填充省市信息，确保上报单位表状态和省市信息准确
     *
     * @param asset 数据资产对象（JSON格式）
     * @return 操作结果
     */
    @PostMapping("/data")
    public ResultVO<Void> addData(@RequestBody DataContentAsset asset) {
        try {
            log.info("新增数据资产 - 资产ID: {}, 资产名称: {}", asset.getId(), asset.getAssetName());

            // 调用Service层新增方法（包含完整的数据校验和同步逻辑）
            dataService.add(asset);

            log.info("新增数据资产成功 - 资产ID: {}", asset.getId());
            return ResultVO.success("新增数据资产成功，ID：" + asset.getId());
        } catch (RuntimeException e) {
            log.error("新增数据资产失败，ID：{}", asset.getId(), e);
            return ResultVO.fail("新增失败：" + e.getMessage());
        }
    }

    @PutMapping("/data")
    public ResultVO<Void> updateData(@RequestBody DataContentAsset asset) {
        try {
            dataService.update(asset);
            return ResultVO.success("修改数据资产成功，ID：" + asset.getId());
        } catch (RuntimeException e) {
            log.error("修改数据资产失败，ID：{}", asset.getId(), e);
            return ResultVO.fail("修改失败：" + e.getMessage());
        }
    }


    // 数据资产删除
    @DeleteMapping("/data/{id}")
    public ResultVO<Void> deleteData(@PathVariable String id) {
        try {
            dataService.remove(id);
            return ResultVO.success("删除数据资产成功，ID：" + id);
        } catch (RuntimeException e) {
            log.error("删除数据资产失败，ID：{}", id, e);
            return ResultVO.fail("删除失败：" + e.getMessage());
        }
    }

    // ==================== 新增开始：辅助方法 ====================
    /**
     * 辅助方法：验证前端传递的 tableType 参数是否合法
     * 作用：检查前端传递的tableType参数是否在允许的范围内
     * 位置：类的最后，作为私有工具方法
     */
    private boolean isValidTableType(String tableType) {
        return "software".equals(tableType) || "cyber".equals(tableType) || "data".equals(tableType);
    }

    /**
     * 辅助方法：获取表类型的中文名称
     * 作用：将英文表类型转换为中文，用于返回给前端的友好提示
     * 位置：类的最后，作为私有工具方法
     */
    private String getTableTypeChineseName(String tableType) {
        switch (tableType) {
            case "software": return "软件资产";
            case "cyber": return "网信资产";
            case "data": return "数据资产";
            default: return "未知";
        }
    }

// ============================== 1117新增：统一导出功能接口 ==============================

    /**
     * 软件资产统一导出接口
     * 访问路径：POST /api/asset/export/software
     * 作用：根据查询条件导出软件资产数据，支持三种导出模式：
     *  1. 无查询条件 → 导出全部数据
     *  2. 有查询条件+分页参数 → 导出当前页数据
     *  3. 有查询条件+无分页参数 → 导出全部匹配数据
     * 请求体示例：
     *  - 导出全部：{} 或 {"pageNum":null, "pageSize":null}
     *  - 导出某单位数据：{"reportUnit": "某单位"}
     *  - 导出当前页：{"reportUnit": "某单位", "pageNum": 1, "pageSize": 20}
     * 技术实现：复用Service层的combinedQuery方法，确保导出与查询结果一致
     */
    @PostMapping("/export/software")
    public void exportSoftwareAssets(@RequestBody SoftwareQueryVO queryVO, HttpServletResponse response) {
        try {
            // 记录导出请求信息，便于问题排查和审计
            log.info("开始处理软件资产导出请求，查询条件: {}", queryVO);

            // 设置Excel响应头，确保浏览器正确识别并下载文件
            setupExcelResponse(response, "软件资产数据");

            // 处理分页逻辑：如果前端未传递分页参数，则导出全部数据
            Integer pageNum = queryVO.getPageNum();
            Integer pageSize = queryVO.getPageSize();
            if (pageNum == null || pageSize == null) {
                pageNum = 1;
                pageSize = Integer.MAX_VALUE;  // 设置超大页面大小，相当于获取全部数据
                log.info("未传递分页参数，设置为导出全部数据");
            } else {
                log.info("使用分页参数导出：pageNum={}, pageSize={}", pageNum, pageSize);
            }

            // 创建分页对象，用于Service层查询
            Page<SoftwareAsset> pageInfo = new Page<>(pageNum, pageSize);

            // 调用Service层联合查询方法，传递所有查询条件
            // 注意：这里传递的是前端实际传入的参数，可能是null，Service层会动态处理
            Page<SoftwareAsset> result = softwareService.combinedQuery(
                    pageInfo,
                    queryVO.getReportUnit(),
                    queryVO.getCategoryCode(),
                    queryVO.getAssetCategory(),
                    queryVO.getAcquisitionMethod(),
                    queryVO.getDeploymentScope(),
                    queryVO.getDeploymentForm(),
                    queryVO.getBearingNetwork(),
                    queryVO.getQuantityMin(),
                    queryVO.getQuantityMax(),
                    queryVO.getServiceStatus(),
                    queryVO.getStartUseDateStart(),
                    queryVO.getStartUseDateEnd(),
                    queryVO.getInventoryUnit()
            );

            // 将查询结果转换为Excel VO格式，确保导出列与导入模板一致
            List<SoftwareAssetExcelVO> excelData = convertToSoftwareExcelVO(result.getRecords());

            // 使用EasyExcel将数据写入HTTP响应流
            EasyExcel.write(response.getOutputStream(), SoftwareAssetExcelVO.class)
                    .sheet("软件资产")  // 设置工作表名称
                    .doWrite(excelData);  // 执行写入操作

            // 记录导出成功信息，便于监控和统计
            log.info("软件资产导出成功，共导出{}条数据", excelData.size());

        } catch (Exception e) {
            // 异常处理：记录详细错误信息并返回用户友好的错误提示
            log.error("软件资产导出失败，查询条件: {}", queryVO, e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 网信资产统一导出接口
     * 访问路径：POST /api/asset/export/cyber
     * 作用：根据查询条件导出网信资产数据，逻辑与软件资产导出相同
     * 特点：支持相同的三种导出模式，确保与查询功能一致性
     */
    @PostMapping("/export/cyber")
    public void exportCyberAssets(@RequestBody CyberQueryVO queryVO, HttpServletResponse response) {
        try {
            log.info("开始处理网信资产导出请求，查询条件: {}", queryVO);

            setupExcelResponse(response, "网信资产数据");

            // 分页逻辑处理
            Integer pageNum = queryVO.getPageNum();
            Integer pageSize = queryVO.getPageSize();
            if (pageNum == null || pageSize == null) {
                pageNum = 1;
                pageSize = Integer.MAX_VALUE;
                log.info("未传递分页参数，设置为导出全部网信资产数据");
            }

            Page<CyberAsset> pageInfo = new Page<>(pageNum, pageSize);

            // 调用网信资产Service查询方法
            Page<CyberAsset> result = cyberService.combinedQuery(
                    pageInfo,
                    queryVO.getReportUnit(),
                    queryVO.getProvince(),
                    queryVO.getCity(),
                    queryVO.getCategoryCode(),
                    queryVO.getAssetCategory(),
                    queryVO.getQuantityMin(),
                    queryVO.getQuantityMax(),
                    queryVO.getUsedQuantityMin(),
                    queryVO.getUsedQuantityMax(),
                    queryVO.getStartUseDateStart(),
                    queryVO.getStartUseDateEnd(),
                    queryVO.getInventoryUnit()
            );

            List<CyberAssetExcelVO> excelData = convertToCyberExcelVO(result.getRecords());

            EasyExcel.write(response.getOutputStream(), CyberAssetExcelVO.class)
                    .sheet("网信资产")
                    .doWrite(excelData);

            log.info("网信资产导出成功，共导出{}条数据", excelData.size());

        } catch (Exception e) {
            log.error("网信资产导出失败，查询条件: {}", queryVO, e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 数据资产统一导出接口
     * 访问路径：POST /api/asset/export/data
     * 作用：根据查询条件导出数据资产数据，逻辑与其他资产导出相同
     * 确保：数据一致性，导出结果与查询页面显示完全一致
     */
    @PostMapping("/export/data")
    public void exportDataAssets(@RequestBody DataContentQueryVO queryVO, HttpServletResponse response) {
        try {
            log.info("开始处理数据资产导出请求，查询条件: {}", queryVO);

            setupExcelResponse(response, "数据资产数据");

            // 分页逻辑处理
            Integer pageNum = queryVO.getPageNum();
            Integer pageSize = queryVO.getPageSize();
            if (pageNum == null || pageSize == null) {
                pageNum = 1;
                pageSize = Integer.MAX_VALUE;
                log.info("未传递分页参数，设置为导出全部数据资产数据");
            }

            Page<DataContentAsset> pageInfo = new Page<>(pageNum, pageSize);

            // 调用数据资产Service查询方法
            Page<DataContentAsset> result = dataService.combinedQuery(
                    pageInfo,
                    queryVO.getReportUnit(),
                    queryVO.getProvince(),
                    queryVO.getCity(),
                    queryVO.getApplicationField(),
                    queryVO.getDevelopmentTool(),
                    queryVO.getQuantityMin(),
                    queryVO.getQuantityMax(),
                    queryVO.getUpdateCycle(),
                    queryVO.getUpdateMethod(),
                    queryVO.getInventoryUnit()
            );

            List<DataContentAssetExcelVO> excelData = convertToDataExcelVO(result.getRecords());

            EasyExcel.write(response.getOutputStream(), DataContentAssetExcelVO.class)
                    .sheet("数据资产")
                    .doWrite(excelData);

            log.info("数据资产导出成功，共导出{}条数据", excelData.size());

        } catch (Exception e) {
            log.error("数据资产导出失败，查询条件: {}", queryVO, e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    // ============================== 新增：省份资产统计接口 ==============================

    /**
     * 接口6(a)：按省份统计三类资产数量和百分比
     * 访问路径：GET /api/asset/statistics/province-asset-overview
     * 作用：统计34个省份+"未知"的三类资产数量及占比

     * 核心逻辑：
     * 1. 分别统计软件、网信、数据三类资产的省份分布
     * 2. 通过关联report_unit表获取准确的省份信息
     * 3. 计算每个省份各类资产占总量的百分比
     * 4. 包含"未知"省份的统计（无法推导出省份的单位）

     * 数据来源：
     * - 软件资产：通过report_unit表关联获取省份
     * - 网信资产：直接使用资产表中的province字段
     * - 数据资产：直接使用资产表中的province字段

     * 返回格式：
     * {
     *   "code": 200,
     *   "message": "成功",
     *   "data": {
     *     "totalSoftwareCount": 1000,
     *     "totalCyberCount": 800,
     *     "totalDataContentCount": 600,
     *     "provinceStats": [
     *       {
     *         "province": "广东省",
     *         "softwareCount": 100,
     *         "softwarePercentage": 10.0,
     *         "cyberCount": 80,
     *         "cyberPercentage": 10.0,
     *         "dataContentCount": 60,
     *         "dataContentPercentage": 10.0
     *       },
     *       {
     *         "province": "未知",
     *         "softwareCount": 50,
     *         "softwarePercentage": 5.0,
     *         "cyberCount": 40,
     *         "cyberPercentage": 5.0,
     *         "dataContentCount": 30,
     *         "dataContentPercentage": 5.0
     *       }
     *     ]
     *   }
     * }
     */
    @GetMapping("/statistics/province-asset-overview")
    public ResultVO<Map<String, Object>> getProvinceAssetOverview() {
        try {
            log.info("开始统计各省份三类资产数量和占比...");

            // 分别获取三类资产的省份统计
            Map<String, Object> softwareStats = softwareService.getProvinceAssetOverview();
            Map<String, Object> cyberStats = cyberService.getProvinceAssetOverview();
            Map<String, Object> dataContentStats = dataService.getProvinceAssetOverview();

            // 合并三个资产表的统计结果
            Map<String, Object> mergedResult = mergeProvinceStats(softwareStats, cyberStats, dataContentStats);

            log.info("省份资产统计完成 - 软件总数: {}, 网信总数: {}, 数据总数: {}",
                    softwareStats.get("totalSoftwareCount"),
                    cyberStats.get("totalCyberCount"),
                    dataContentStats.get("totalDataContentCount"));

            return ResultVO.success(mergedResult, "获取省份资产统计成功");
        } catch (Exception e) {
            log.error("获取省份资产统计失败", e);
            return ResultVO.fail("统计失败：" + e.getMessage());
        }
    }

    /**
     * 接口6(b)：按省份和资产类型统计资产分类细分
     * 访问路径：GET /api/asset/statistics/province-asset-detail
     * 参数：province(必填), assetType(必填: software/cyber/data)
     * 作用：统计指定省份下指定资产类型的各资产分类数量和占比

     * 核心逻辑：
     * 1. 验证省份和资产类型参数的合法性
     * 2. 根据资产类型调用对应的Service方法
     * 3. 统计该省份下各资产分类的数量
     * 4. 计算各分类在该省份中的占比

     * 返回格式：
     * {
     *   "code": 200,
     *   "message": "成功",
     *   "data": {
     *     "province": "广东省",
     *     "assetType": "software",
     *     "totalCount": 100,
     *     "categoryStats": [
     *       {
     *         "categoryName": "操作系统",
     *         "count": 20,
     *         "percentage": 20.0
     *       },
     *       {
     *         "categoryName": "数据库系统",
     *         "count": 15,
     *         "percentage": 15.0
     *       }
     *     ]
     *   }
     * }
     */
    @GetMapping("/statistics/province-asset-detail")
    public ResultVO<Map<String, Object>> getProvinceAssetDetail(
            @RequestParam String province,
            @RequestParam String assetType) {
        try {
            log.info("开始统计省份资产分类细分 - 省份: {}, 资产类型: {}", province, assetType);

            // 验证资产类型参数
            if (!isValidAssetType(assetType)) {
                return ResultVO.fail("资产类型参数不合法，必须是: software, cyber, data");
            }

            Map<String, Object> result;
            switch (assetType) {
                case "software":
                    result = softwareService.getProvinceAssetCategoryDetail(province);
                    break;
                case "cyber":
                    result = cyberService.getProvinceAssetCategoryDetail(province);
                    break;
                case "data":
                    result = dataService.getProvinceAssetCategoryDetail(province);
                    break;
                default:
                    throw new RuntimeException("不支持的资产类型: " + assetType);
            }

            log.info("省份资产分类细分统计完成 - 省份: {}, 资产类型: {}, 总数: {}",
                    province, assetType, result.get("totalCount"));

            return ResultVO.success(result, "获取省份资产分类细分成功");
        } catch (Exception e) {
            log.error("获取省份资产分类细分失败", e);
            return ResultVO.fail("统计失败：" + e.getMessage());
        }
    }

// ============================== 省份资产统计辅助方法 ==============================

    /**
     * 辅助方法：验证资产类型参数是否合法
     * 作用：确保前端传递的assetType参数在允许的范围内
     * 修改：将dataContent改为data，保持接口统一性
     *
     * @param assetType 资产类型参数
     * @return 参数合法返回true，否则返回false
     */
    private boolean isValidAssetType(String assetType) {
        return "software".equals(assetType) || "cyber".equals(assetType) || "data".equals(assetType);
    }

    /**
     * 辅助方法：合并三个资产表的省份统计结果
     * 作用：将软件、网信、数据三类资产的省份统计合并为一个统一的结果集
     * 修改：将"其他"改为"未知"，与自动补全省市工具保持一致

     * 合并逻辑：
     * 1. 收集所有可能的省份（34个标准省份+"未知"）
     * 2. 为每个省份初始化三类资产的统计值（默认为0）
     * 3. 分别填充三类资产的实际统计数据
     * 4. 确保每个省份都有完整的三类资产统计信息
     *
     * @param softwareStats 软件资产统计结果
     * @param cyberStats 网信资产统计结果
     * @param dataContentStats 数据资产统计结果
     * @return 合并后的完整省份统计结果
     */
    private Map<String, Object> mergeProvinceStats(
            Map<String, Object> softwareStats,
            Map<String, Object> cyberStats,
            Map<String, Object> dataContentStats) {

        Map<String, Object> result = new HashMap<>();

        // 保存总数
        result.put("totalSoftwareCount", softwareStats.get("totalSoftwareCount"));
        result.put("totalCyberCount", cyberStats.get("totalCyberCount"));
        result.put("totalDataContentCount", dataContentStats.get("totalDataContentCount"));

        // 合并各省份统计
        List<Map<String, Object>> mergedProvinceStats = new ArrayList<>();

        // 获取所有可能的省份（包括"未知"）
        Set<String> allProvinces = new HashSet<>();
        allProvinces.add("未知");  // 修改：将"其他"改为"未知"

        // 添加34个标准省份
        allProvinces.addAll(Arrays.asList(
                "北京市", "天津市", "河北省", "山西省", "内蒙古自治区", "辽宁省", "吉林省", "黑龙江省",
                "上海市", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省", "湖北省",
                "湖南省", "广东省", "广西壮族自治区", "海南省", "重庆市", "四川省", "贵州省", "云南省",
                "西藏自治区", "陕西省", "甘肃省", "青海省", "宁夏回族自治区", "新疆维吾尔自治区", "台湾省",
                "香港特别行政区", "澳门特别行政区"
        ));

        // 为每个省份创建统计记录
        for (String province : allProvinces) {
            Map<String, Object> provinceStat = new HashMap<>();
            provinceStat.put("province", province);

            // 设置软件资产统计（默认为0）
            provinceStat.put("softwareCount", 0L);
            provinceStat.put("softwarePercentage", 0.0);

            // 设置网信资产统计（默认为0）
            provinceStat.put("cyberCount", 0L);
            provinceStat.put("cyberPercentage", 0.0);

            // 设置数据资产统计（默认为0）
            provinceStat.put("dataContentCount", 0L);
            provinceStat.put("dataContentPercentage", 0.0);

            mergedProvinceStats.add(provinceStat);
        }

        // 填充实际数据
        fillActualStats(mergedProvinceStats, (List<Map<String, Object>>) softwareStats.get("softwareProvinceStats"), "software");
        fillActualStats(mergedProvinceStats, (List<Map<String, Object>>) cyberStats.get("cyberProvinceStats"), "cyber");
        fillActualStats(mergedProvinceStats, (List<Map<String, Object>>) dataContentStats.get("dataContentProvinceStats"), "dataContent");

        result.put("provinceStats", mergedProvinceStats);
        return result;
    }

    /**
     * 辅助方法：填充实际统计数据到合并结果中
     * 作用：将各类资产的实际统计数据填充到合并结果集的对应省份中
     * 修改：将"其他"改为"未知"，与自动补全省市工具保持一致

     * 填充逻辑：
     * 1. 遍历实际统计数据
     * 2. 在合并结果中找到对应的省份记录
     * 3. 更新该省份的对应资产类型统计信息
     * 4. 处理"未知"省份的特殊情况
     *
     * @param mergedStats 合并后的统计结果
     * @param actualStats 实际统计数据
     * @param assetType 资产类型标识
     */
    private void fillActualStats(List<Map<String, Object>> mergedStats,
                                 List<Map<String, Object>> actualStats,
                                 String assetType) {
        for (Map<String, Object> actualStat : actualStats) {
            String province = (String) actualStat.get("province");
            Long count = (Long) actualStat.get(assetType + "Count");
            Double percentage = (Double) actualStat.get(assetType + "Percentage");

            // 处理省份名称为null的情况，统一转为"未知"
            if (province == null) {
                province = "未知";
            }

            // 在合并结果中找到对应的省份记录
            for (Map<String, Object> mergedStat : mergedStats) {
                if (province.equals(mergedStat.get("province"))) {
                    mergedStat.put(assetType + "Count", count);
                    mergedStat.put(assetType + "Percentage", percentage);
                    break;
                }
            }
        }
    }

// ============================== 导出辅助方法 ==============================

    /**
     * 设置Excel响应头
     * 作用：配置HTTP响应头，确保浏览器正确识别并下载Excel文件
     * 包含内容：
     *  - Content-Type：设置为Excel文件类型
     *  - Content-Disposition：触发浏览器下载，包含文件名
     *  - 缓存控制：禁用缓存，确保每次下载都是最新数据
     * 技术细节：对中文文件名进行URL编码，解决浏览器中文文件名乱码问题
     */
    private void setupExcelResponse(HttpServletResponse response, String fileName) {
        try {
            // 对文件名进行URL编码，解决中文文件名乱码问题
            // replaceAll("\\+", "%20") 处理空格编码问题
            String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    .replaceAll("\\+", "%20");

            // 设置响应内容类型为Excel格式
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");

            // 设置Content-Disposition头，触发浏览器下载行为
            // 文件名包含时间戳，避免重复下载时文件名冲突
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + encodedFileName + "_" +
                            System.currentTimeMillis() + ".xlsx");

            // 禁用缓存，确保每次下载都是最新数据
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);

            log.debug("Excel响应头设置完成，文件名: {}", encodedFileName);
        } catch (Exception e) {
            log.error("设置Excel响应头失败", e);
            throw new RuntimeException("设置响应头失败", e);
        }
    }

    /**
     * 转换软件资产数据为Excel VO
     * 作用：将SoftwareAsset实体对象转换为SoftwareAssetExcelVO对象
     * 确保：导出文件的列格式与导入模板完全一致，避免格式不匹配问题
     * 注意：金额字段需要从BigDecimal转换为Double，因为EasyExcel处理Double更友好
     */
    private List<SoftwareAssetExcelVO> convertToSoftwareExcelVO(List<SoftwareAsset> assets) {
        log.info("开始转换{}条软件资产数据为Excel格式", assets.size());

        return assets.stream().map(asset -> {
            SoftwareAssetExcelVO vo = new SoftwareAssetExcelVO();
            // 基础信息字段
            vo.setId(asset.getId());
            vo.setTitle(asset.getTitle());
            vo.setDataAuditOpinion(asset.getDataAuditOpinion());
            vo.setReportUnit(asset.getReportUnit());
            vo.setCategoryCode(asset.getCategoryCode());
            vo.setAssetCategory(asset.getAssetCategory());
            vo.setAssetName(asset.getAssetName());

            // 技术属性字段
            vo.setAcquisitionMethod(asset.getAcquisitionMethod());
            vo.setFunctionBrief(asset.getFunctionBrief());
            vo.setDeploymentScope(asset.getDeploymentScope());
            vo.setDeploymentForm(asset.getDeploymentForm());
            vo.setBearingNetwork(asset.getBearingNetwork());
            vo.setSoftwareCopyright(asset.getSoftwareCopyright());

            // 数量金额字段
            vo.setActualQuantity(asset.getActualQuantity());
            vo.setUnit(asset.getUnit());
            // BigDecimal转Double，避免EasyExcel处理问题
            vo.setUnitPrice(asset.getUnitPrice() != null ? asset.getUnitPrice().doubleValue() : null);
            vo.setAmount(asset.getAmount() != null ? asset.getAmount().doubleValue() : null);

            // 状态和管理字段
            vo.setPricingMethod(asset.getPricingMethod());
            vo.setPricingDescription(asset.getPricingDescription());
            vo.setServiceStatus(asset.getServiceStatus());
            vo.setPutIntoUseDate(asset.getPutIntoUseDate());
            vo.setInventoryUnit(asset.getInventoryUnit());

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 转换网信资产数据为Excel VO
     * 作用：将CyberAsset实体对象转换为CyberAssetExcelVO对象
     * 确保：导出列与网信资产导入模板完全匹配
     */
    private List<CyberAssetExcelVO> convertToCyberExcelVO(List<CyberAsset> assets) {
        log.info("开始转换{}条网信资产数据为Excel格式", assets.size());

        return assets.stream().map(asset -> {
            CyberAssetExcelVO vo = new CyberAssetExcelVO();
            // 基础信息字段
            vo.setId(asset.getId());
            vo.setReportUnit(asset.getReportUnit());
            vo.setProvince(asset.getProvince());
            vo.setCity(asset.getCity());
            vo.setCategoryCode(asset.getCategoryCode());
            vo.setAssetCategory(asset.getAssetCategory());
            vo.setAssetName(asset.getAssetName());
            vo.setAssetContent(asset.getAssetContent());
            vo.setSupportObject(asset.getSupportObject());

            // 数量金额字段
            vo.setActualQuantity(asset.getActualQuantity());
            vo.setUnit(asset.getUnit());
            vo.setUsedQuantity(asset.getUsedQuantity());
            // BigDecimal转Double
            vo.setUnitPrice(asset.getUnitPrice() != null ? asset.getUnitPrice().doubleValue() : null);
            vo.setAmount(asset.getAmount() != null ? asset.getAmount().doubleValue() : null);

            // 管理字段
            vo.setPricingMethod(asset.getPricingMethod());
            vo.setPricingDescription(asset.getPricingDescription());
            vo.setPutIntoUseDate(asset.getPutIntoUseDate());
            vo.setInventoryUnit(asset.getInventoryUnit());
            vo.setInventoryRemark(asset.getInventoryRemark());
            vo.setValuationRemark(asset.getValuationRemark());
            vo.setOriginalAccountRemark(asset.getOriginalAccountRemark());

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 转换数据资产数据为Excel VO
     * 作用：将DataContentAsset实体对象转换为DataContentAssetExcelVO对象
     * 确保：导出列与数据资产导入模板完全一致
     */
    private List<DataContentAssetExcelVO> convertToDataExcelVO(List<DataContentAsset> assets) {
        log.info("开始转换{}条数据资产数据为Excel格式", assets.size());

        return assets.stream().map(asset -> {
            DataContentAssetExcelVO vo = new DataContentAssetExcelVO();
            // 基础信息字段
            vo.setId(asset.getId());
            vo.setReportUnit(asset.getReportUnit());
            vo.setProvince(asset.getProvince());
            vo.setCity(asset.getCity());
            vo.setCategoryCode(asset.getCategoryCode());
            vo.setAssetCategory(asset.getAssetCategory());
            vo.setAssetName(asset.getAssetName());
            vo.setDataType(asset.getDataType());
            vo.setAcquisitionMethod(asset.getAcquisitionMethod());
            vo.setFunctionBrief(asset.getFunctionBrief());

            // 应用和技术字段
            vo.setApplicationField(asset.getApplicationField());
            vo.setDevelopmentTool(asset.getDevelopmentTool());

            // 数量金额字段
            vo.setActualQuantity(asset.getActualQuantity());
            vo.setUnit(asset.getUnit());
            // BigDecimal转Double
            vo.setUnitPrice(asset.getUnitPrice() != null ? asset.getUnitPrice().doubleValue() : null);
            vo.setAmount(asset.getAmount() != null ? asset.getAmount().doubleValue() : null);

            // 更新和管理字段
            vo.setPricingMethod(asset.getPricingMethod());
            vo.setPricingDescription(asset.getPricingDescription());
            vo.setUpdateCycle(asset.getUpdateCycle());
            vo.setUpdateMethod(asset.getUpdateMethod());
            vo.setInventoryUnit(asset.getInventoryUnit());

            return vo;
        }).collect(Collectors.toList());
    }


}