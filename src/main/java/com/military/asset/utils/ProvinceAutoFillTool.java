package com.military.asset.utils;

import com.military.asset.entity.HasReportUnitAndProvince;
import com.military.asset.entity.ReportUnit;
import com.military.asset.mapper.ReportUnitMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.AllArgsConstructor;
import java.util.List; // 🆕 新增导入

/**
 * 省市自动填充与上报单位表同步核心工具类

 * ==================== 核心功能概述 ====================
 * 本工具类为军工资产管理系统提供统一的省市自动填充和上报单位表状态同步功能。
 * 主要服务于网信基础资产表和数据内容资产表，确保省市信息与上报单位的一致性，
 * 并实时维护上报单位表在各资产表中的状态标志。

 * ==================== 自动补全逻辑 ====================
 * 场景1：有省有市 → 直接保留用户输入
 * 场景2：有省无市 → 用省补充市（查找首府）
 * 场景3：有市无省 → 用市补充省（反向推导）
 * 场景4：省市都空 → 按单位推导（县级信息 → 城市信息 → 省份信息 → 战区信息 → 未知）

 * ==================== 推导优先级说明 ====================
 * 1. 县级信息优先：通过县级单位推导对应的省市（最具体）
 * 2. 城市信息次之：通过城市推导对应的省份（次具体）
 * 3. 省份信息再次：通过省份自动补充首府城市（最宏观）
 * 4. 战区信息补充：纯战区单位映射到固定省市
 * 5. 默认值处理：无法识别时使用"未知"

 * ==================== 核心设计理念 ====================
 * 1. 用户输入优先原则
 *    - 用户明确填写的省市信息具有最高优先级
 *    - 自动推导只在省市完全为空时进行
 *    - 绝不覆盖用户已填写的信息

 * 2. 数据一致性原则
 *    - 确保省市信息与上报单位的地理位置匹配
 *    - 维护上报单位表状态与实际数据存在性一致
 *    - 支持Excel导入和手动操作的数据同步

 * 3. 状态独立管理原则
 *    - 各资产表状态完全独立，互不影响
 *    - 软件资产表状态：仅取决于软件资产表中是否有该单位数据
 *    - 网信资产表状态：仅取决于网信资产表中是否有该单位数据
 *    - 数据资产表状态：仅取决于数据内容资产表中是否有该单位数据

 * 4. 操作顺序无关原则
 *    - 无论三类资产以什么顺序导入，都能确保每个资产表的状态正确
 *    - 每次操作都重新检查所有资产表的状态，确保状态准确反映实际数据
 *    - 状态更新基于实际数据存在性，不受导入顺序影响

 * ==================== 主要应用场景 ====================
 * 场景1：Excel批量导入
 *   - 支持省市信息的智能填充和补全
 *   - 自动同步上报单位表状态
 *   - 处理Excel中省市信息不全或格式不一致的情况

 * 场景2：前端手动新增/修改
 *   - 根据上报单位自动推导省市信息
 *   - 支持上报单位变更时的智能省市更新
 *   - 实时同步上报单位表状态变化

 * 场景3：数据删除操作
 *   - 自动更新上报单位表状态标志
 *   - 确保状态准确反映剩余数据量
 *   - 支持批量删除的状态同步

 * ==================== 核心算法逻辑 ====================
 * 1. 省市自动填充算法（fillAssetProvinceCity）
 *    - 输入：资产对象、操作模式标识
 *    - 输出：填充完整省市信息的资产对象
 *    - 处理流程：
 *        a. 标准化处理：统一省市名称格式
 *        b. 模式判断：更新模式 vs 新增/导入模式
 *        c. 场景处理：4种主要场景的优先级处理
 *        d. 推导逻辑：县级信息 → 城市信息 → 省份信息 → 战区信息 → 默认值

 * 2. 上报单位表同步算法（syncReportUnit）
 *    - 输入：单位名称、省份、资产类型、操作类型
 *    - 输出：更新后的上报单位表记录
 *    - 处理流程：
 *        a. 软件资产省份推导（如需要）
 *        b. 单位记录存在性检查
 *        c. 所有资产表状态刷新
 *        d. 省份信息更新（如需要）
 *        e. 状态持久化保存

 * 3. 批量同步优化算法（batchSyncReportUnits）
 *    - 输入：批量同步请求列表
 *    - 输出：批量处理结果
 *    - 优化策略：
 *        a. 请求合并：相同单位只处理一次
 *        b. 性能优化：减少数据库连接次数
 *        c. 错误隔离：单单位失败不影响整体

 * ==================== 关键技术特性 ====================
 * 1. 智能推导：基于单位名称的省市智能匹配
 * 2. 战区识别：支持五大战区自动映射
 * 3. 格式标准化：统一省市名称格式，避免数据不一致
 * 4. 状态一致性：确保数据库状态与实际数据完全一致
 * 5. 性能优化：支持批量操作，减少数据库压力
 * 6. 容错处理：完善的异常处理和日志记录
 * 7. 扩展性：支持新的资产类型扩展

 * ==================== 使用注意事项 ====================
 * 1. 软件资产表没有省市字段，相关操作传递null值
 * 2. 更新模式会强制重新推导省市，覆盖原有值
 * 3. 新增/导入模式尊重Excel原有值，仅在空值时填充
 * 4. 状态同步基于实际数据统计，确保准确性
 * 5. 批量操作时注意事务边界和性能影响
 *
 * ==================== 上报单位表的自动清理机制 -- cleanupZeroStatusRecords方法 ====================
 * 本工具提供自动清理无效上报单位记录的功能，确保数据库数据的精简高效。
 *
 * 清理条件：当上报单位在三个资产表中的状态标志均为0时（表示无数据），
 *           系统会自动删除该记录，避免数据冗余。
 *
 * 双重保险：
 * 1. 即时清理：单个操作后立即检查并清理当前单位
 * 2. 批量清理：批量操作后全面扫描并清理所有无效记录
 *
 */
@Component
public class ProvinceAutoFillTool {

    // ============================ 依赖注入 ============================

    /**
     * 省市字典缓存工具：负责处理省市字段的自动填充逻辑
     * 提供省份首府查询、城市到省份映射等核心功能
     */
    @Resource
    private AreaCacheTool areaCacheTool;

    /**
     * 上报单位表Mapper：操作数据库，用于同步上报单位状态
     * 提供单位查询、数量统计等数据库操作
     */
    @Resource
    private ReportUnitMapper reportUnitMapper;

    // ============================ 新增：战区映射常量 ============================
    private static final Map<String, String> WAR_ZONE_MAPPING = new HashMap<>();
    static {
        WAR_ZONE_MAPPING.put("东部战区", "江苏省-南京市");
        WAR_ZONE_MAPPING.put("南部战区", "广东省-广州市");
        WAR_ZONE_MAPPING.put("西部战区", "四川省-成都市");
        WAR_ZONE_MAPPING.put("北部战区", "辽宁省-沈阳市");
        WAR_ZONE_MAPPING.put("中部战区", "北京市-北京市");
        // 支持简写
        WAR_ZONE_MAPPING.put("东部", "江苏省-南京市");
        WAR_ZONE_MAPPING.put("南部", "广东省-广州市");
        WAR_ZONE_MAPPING.put("西部", "四川省-成都市");
        WAR_ZONE_MAPPING.put("北部", "辽宁省-沈阳市");
        WAR_ZONE_MAPPING.put("中部", "北京市-北京市");
    }

    // ============================ 核心方法 ============================

    /**
     * 核心1：资产表省市自动填充（整合所有场景）
     * 处理逻辑：Excel有值优先 → 部分缺失补全 → 无值则按上报单位推导
     *
     * @param asset 资产实体（网信/数据内容资产，必须实现HasReportUnitAndProvince接口）
     * @param isUpdate 是否为"修改上报单位"场景
     *                true=修改场景（强制重新推导省市）
     *                false=新增/导入场景（尊重Excel值）
     */
    public void fillAssetProvinceCity(HasReportUnitAndProvince asset, boolean isUpdate) {
        String excelProvince = asset.getProvince();
        String excelCity = asset.getCity();
        String unitName = asset.getReportUnit();

        System.out.println("=== 开始省市自动填充 ===");
        System.out.println("原始数据 - 省: '" + excelProvince + "', 市: '" + excelCity + "', 单位: '" + unitName + "'");
        System.out.println("是否为更新模式: " + isUpdate);

        // ============ 新增：省市信息标准化处理 ============
        // 确保Excel导入和自动填充使用相同的标准格式，避免"四川"和"四川省"同时出现
        if (hasValue(excelProvince)) {
            String standardizedProvince = standardizeProvinceName(excelProvince);
            if (!excelProvince.equals(standardizedProvince)) {
                System.out.println("省份标准化: '" + excelProvince + "' -> '" + standardizedProvince + "'");
                excelProvince = standardizedProvince;
                asset.setProvince(standardizedProvince);
            }
        }

        if (hasValue(excelCity)) {
            String standardizedCity = standardizeCityName(excelCity);
            if (!excelCity.equals(standardizedCity)) {
                System.out.println("城市标准化: '" + excelCity + "' -> '" + standardizedCity + "'");
                excelCity = standardizedCity;
                asset.setCity(standardizedCity);
            }
        }

        // 先检查 AreaCacheTool 是否正常初始化
        if (areaCacheTool == null) {
            System.out.println("ERROR: areaCacheTool 未注入!");
            return;
        }

        // 验证缓存
        areaCacheTool.validateCache();

        // 场景A：修改上报单位（强制重新推导，覆盖原有省市）
        if (isUpdate) {
            System.out.println("进入更新模式，强制重新推导");
            deriveByUnitName(asset, unitName);
            System.out.println("更新后结果 - 省: " + asset.getProvince() + ", 市: " + asset.getCity());
            return;
        }

        // 场景B：Excel导入/新增（按Excel值优先级处理）
        // 子场景1：Excel省、市都有值
        if (hasValue(excelProvince) && hasValue(excelCity)) {
            System.out.println("场景1: Excel省市齐全，使用Excel值");
            return;
        }

        // 子场景2：Excel只有省，无市
        if (hasValue(excelProvince) && !hasValue(excelCity)) {
            System.out.println("场景2: 只有省无市，补全首府");
            try {
                String capital = areaCacheTool.getCapitalByProvinceName(excelProvince);
                System.out.println("省份 '" + excelProvince + "' 的首府是: " + capital);
                if (hasValue(capital)) {
                    asset.setCity(capital);
                    System.out.println("成功设置首府 - 省: " + asset.getProvince() + ", 市: " + asset.getCity());
                } else {
                    System.out.println("ERROR: 未找到省份 '" + excelProvince + "' 的首府");
                    // 尝试按单位推导
                    deriveByUnitName(asset, unitName);
                }
            } catch (Exception e) {
                System.out.println("ERROR: 获取首府时出错: " + e.getMessage());
                deriveByUnitName(asset, unitName);
            }
            return;
        }

        // 子场景3：Excel只有市，无省
        if (!hasValue(excelProvince) && hasValue(excelCity)) {
            System.out.println("场景3: 只有市无省，推导省份");
            try {
                String province = areaCacheTool.getCityToProvinceMap().get(excelCity);
                System.out.println("城市 '" + excelCity + "' 对应的省份是: " + province);
                if (hasValue(province)) {
                    asset.setProvince(province);
                    System.out.println("成功设置省份 - 省: " + asset.getProvince() + ", 市: " + asset.getCity());
                } else {
                    System.out.println("ERROR: 未找到城市 '" + excelCity + "' 对应的省份");
                    // 尝试按单位推导
                    deriveByUnitName(asset, unitName);
                }
            } catch (Exception e) {
                System.out.println("ERROR: 获取省份时出错: " + e.getMessage());
                deriveByUnitName(asset, unitName);
            }
            return;
        }

        // 子场景4：Excel省、市都空
        System.out.println("场景4: 省市都为空，按单位推导");
        deriveByUnitName(asset, unitName);
        System.out.println("推导后结果 - 省: " + asset.getProvince() + ", 市: " + asset.getCity());
    }

    /**
     * 核心2：同步上报单位表（资产表新增/删除时调用）
     * 处理逻辑：每个资产表状态完全独立，只取决于对应表中是否有该单位的数据

     * 核心修正：无论三类资产以什么顺序导入，都能确保每个资产表的状态正确
     * - 每次操作都重新检查所有资产表的状态，确保状态准确反映实际数据
     * - 状态更新基于实际数据存在性，不受导入顺序影响
     * - 软件资产：自动根据单位名称推导省份，避免空省份覆盖已有省份
     *
     * @param unitName 上报单位名称（新/旧）
     * @param province 资产表填充的省（用于同步，软件资产传null）
     * @param assetType 资产类型标识
     *                 "software"=软件资产
     *                 "cyber"=网信资产
     *                 "dataContent"=数据内容资产
     * @param isDelete 是否为删除场景
     *                 true=删除场景（检查剩余数据）
     *                 false=新增/修改场景（标记有数据）
     */
    public void syncReportUnit(String unitName, String province, String assetType, boolean isDelete) {
        // ============ 软件资产省份推导逻辑 ============
        // 如果省份为null（主要是软件资产），尝试根据单位名称推导省份
        if (province == null && !isDelete) {
            province = deriveProvinceFromUnitName(unitName);
            System.out.println("软件资产推导省份: 单位=" + unitName + ", 推导省份=" + province);
        }

        // ============ 核心修正：无论什么操作，都重新检查所有资产表状态 ============
        // 确保状态准确反映实际数据，不受导入顺序影响
        ReportUnit reportUnit = reportUnitMapper.selectByReportUnitName(unitName);

        if (reportUnit == null && !isDelete) {
            // 无记录→新建上报单位记录（仅限新增操作）
            reportUnit = new ReportUnit();
            reportUnit.setReportUnit(unitName);
            reportUnit.setProvince(province);

            // 初始化所有状态字段为0
            reportUnit.setSource_table_cyber_asset((short) 0);
            reportUnit.setSource_table_data_content_asset((short) 0);
            reportUnit.setSource_table_software_asset((short) 0);

            reportUnitMapper.insert(reportUnit);
            System.out.println("新增上报单位：" + unitName);
        }

        // ============ 重新获取最新记录（确保操作的是最新数据） ============
        reportUnit = reportUnitMapper.selectByReportUnitName(unitName);
        if (reportUnit == null) {
            System.out.println("ERROR: 上报单位记录不存在，单位：" + unitName);
            return;
        }

        // ============ 更新省份信息（仅限新增/修改操作） ============
        if (!isDelete && province != null && !province.trim().isEmpty()) {
            reportUnit.setProvince(province);
            System.out.println("更新上报单位省份：" + unitName + " -> " + province);
        }

        // ============ 核心修正：重新检查并更新所有资产表状态 ============
        // 无论什么操作，都基于实际数据重新设置所有状态
        refreshAllAssetStatus(reportUnit, unitName);

        // ============ 保存更新 ============
        reportUnitMapper.updateById(reportUnit);

        // ============ 记录操作日志 ============
        if (isDelete) {
            System.out.println("资产删除操作完成，单位：" + unitName + "，资产类型：" + assetType);
        } else {
            System.out.println("资产新增/修改操作完成，单位：" + unitName + "，资产类型：" + assetType);
        }

        // ============ 输出最终状态 ============
        System.out.println("最终状态 - " + unitName +
                " [软件:" + reportUnit.getSource_table_software_asset() +
                ", 网信:" + reportUnit.getSource_table_cyber_asset() +
                ", 数据:" + reportUnit.getSource_table_data_content_asset() + "]");

        // ============ 🆕 新增：检查并删除三个状态都为0的记录 ============
        if (reportUnit.getSource_table_software_asset() == 0 &&
                reportUnit.getSource_table_cyber_asset() == 0 &&
                reportUnit.getSource_table_data_content_asset() == 0) {

            System.out.println("🗑️ 检测到上报单位三个状态均为0，执行自动删除: " + unitName);
            reportUnitMapper.deleteById(reportUnit.getId()); // 或者使用 deleteByReportUnitName(unitName)
            System.out.println("✅ 已删除无效上报单位: " + unitName);
        }
    }

    // ============================ 辅助方法 ============================

    /**
     * 辅助1：按上报单位推导省市（新优先级逻辑）

     * 推导优先级：县级信息 → 城市信息 → 省份信息 → 战区信息 → 默认"未知"

     * 设计理念：
     * - 县级信息最具体，能准确推导出省市
     * - 城市信息次之，能推导出省份
     * - 省份信息最宏观，只能补充首府城市
     * - 战区信息作为特殊情况的补充
     * - 确保推导结果尽可能准确具体
     *
     * @param asset 资产实体
     * @param unitName 上报单位名称
     */
    private void deriveByUnitName(HasReportUnitAndProvince asset, String unitName) {
        System.out.println("开始按单位推导省市，单位名称: " + unitName);

        if (!hasValue(unitName)) {
            asset.setProvince("未知");
            asset.setCity("");
            System.out.println("单位名称为空，设置为默认值: 未知-空");
            return;
        }

        // ============ 第一步：县级单位匹配（最具体） ============
        System.out.println("第一步：查找县级单位信息");
        String countyResult = deriveFromCounty(unitName);
        if (countyResult != null) {
            String[] provinceCity = countyResult.split("-");
            asset.setProvince(provinceCity[0]);
            asset.setCity(provinceCity[1]);
            System.out.println("匹配到县级单位: " + unitName + " → " + countyResult);
            return;
        }

        // ============ 第二步：城市信息匹配（次具体） ============
        System.out.println("第二步：查找城市信息");

        // 2.1 城市全称匹配
        Map<String, String> cityMap = areaCacheTool.getCityToProvinceMap();
        System.out.println("正在匹配城市全称列表，共" + areaCacheTool.getAllCityNames().size() + "个城市");
        for (String city : areaCacheTool.getAllCityNames()) {
            if (unitName.contains(city)) {
                String province = cityMap.get(city);
                asset.setProvince(province);
                asset.setCity(city);
                System.out.println("匹配到城市全称: " + city + "，推导省份: " + province + "，设置省市为: " + province + "-" + city);
                return;
            }
        }

        // 2.2 城市简写匹配
        System.out.println("正在匹配城市简写");
        for (String city : areaCacheTool.getAllCityNames()) {
            String cityAbbr = getCityAbbreviation(city);
            if (hasValue(cityAbbr) && unitName.contains(cityAbbr)) {
                String province = cityMap.get(city);
                asset.setProvince(province);
                asset.setCity(city);
                System.out.println("匹配到城市简写: " + cityAbbr + " → " + city + "，推导省份: " + province);
                return;
            }
        }

        System.out.println("第二步完成：未匹配到任何城市信息");

        // ============ 第三步：省份信息匹配（最宏观） ============
        System.out.println("第三步：查找省份信息");

        // 3.1 省份全称匹配
        System.out.println("正在匹配省份全称列表，共" + areaCacheTool.getAllProvinceNames().size() + "个省份");
        for (String province : areaCacheTool.getAllProvinceNames()) {
            if (unitName.contains(province)) {
                String capital = areaCacheTool.getCapitalByProvinceName(province);
                asset.setProvince(province);
                asset.setCity(capital);
                System.out.println("匹配到省份全称: " + province + "，首府: " + capital + "，设置省市为: " + province + "-" + capital);
                return;
            }
        }

        // 3.2 省份简写匹配
        System.out.println("正在匹配省份简写");
        for (String province : areaCacheTool.getAllProvinceNames()) {
            String provinceAbbr = getProvinceAbbreviation(province);
            if (hasValue(provinceAbbr) && unitName.contains(provinceAbbr)) {
                String capital = areaCacheTool.getCapitalByProvinceName(province);
                asset.setProvince(province);
                asset.setCity(capital);
                System.out.println("匹配到省份简写: " + provinceAbbr + " → " + province + "，首府: " + capital);
                return;
            }
        }

        System.out.println("第三步完成：未匹配到任何省份信息");

        // ============ 第四步：战区信息匹配（特殊情况） ============
        System.out.println("第四步：查找战区信息");
        for (String warZone : WAR_ZONE_MAPPING.keySet()) {
            if (unitName.contains(warZone)) {
                String[] provinceCity = WAR_ZONE_MAPPING.get(warZone).split("-");
                asset.setProvince(provinceCity[0]);
                asset.setCity(provinceCity[1]);
                System.out.println("匹配到战区: " + warZone + "，设置省市为: " + provinceCity[0] + "-" + provinceCity[1]);
                return;
            }
        }

        System.out.println("第四步完成：未匹配到任何战区信息");

        // ============ 第五步：都无结果 → 填"未知" ============
        asset.setProvince("未知");
        asset.setCity("");
        System.out.println("第五步：所有匹配规则都失败，设置为默认值: 未知-空");
    }

    /**
     * 辅助2：统计资产表中该单位的剩余数量（删除时判断用）
     * 重要：只统计当前资产表的数据，不影响其他资产表
     *
     * @param unitName 上报单位名称
     * @param assetType 资产类型
     * @return 当前资产表的剩余记录数量
     */
    private long countAssetByUnit(String unitName, String assetType) {
        return switch (assetType) {
            case "software" -> reportUnitMapper.countSoftwareAsset(unitName);
            case "cyber" -> reportUnitMapper.countCyberAsset(unitName);
            case "dataContent" -> reportUnitMapper.countDataContentAsset(unitName);
            default -> 0;
        };
    }

    /**
     * 辅助3：设置上报单位表的归属状态（source_table_xxx）
     * 重要：只设置指定资产表的状态，其他资产表状态保持不变
     *
     * @param reportUnit 上报单位实体
     * @param assetType 资产类型
     * @param status 状态值（1=有数据，0=无数据）
     */
    private void setSourceStatus(ReportUnit reportUnit, String assetType, short status) {
        switch (assetType) {
            case "software" -> reportUnit.setSource_table_software_asset(status);
            case "cyber" -> reportUnit.setSource_table_cyber_asset(status);
            case "dataContent" -> reportUnit.setSource_table_data_content_asset(status);
        }
    }

    /**
     * 辅助4：判断字符串是否有值（避免null和空字符串）
     *
     * @param str 待检查字符串
     * @return 有值返回true，否则返回false
     */
    private boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 辅助5：根据上报单位名称推导省份（新优先级逻辑，主要用于软件资产）

     * 推导规则：县级信息 → 城市信息 → 省份信息 → 战区信息 → 默认"未知"
     * 注意：软件资产表没有省市字段，此方法仅用于推导省份并同步到上报单位表
     *
     * @param unitName 上报单位名称
     * @return 推导出的省份名称，如果无法推导则返回"未知"
     */
    private String deriveProvinceFromUnitName(String unitName) {
        if (!hasValue(unitName)) {
            return "未知";
        }

        // ============ 第一步：县级单位匹配 ============
        String countyResult = deriveFromCounty(unitName);
        if (countyResult != null) {
            String province = countyResult.split("-")[0];
            System.out.println("单位名称匹配到县级: " + unitName + " → " + countyResult + " → " + province);
            return province;
        }

        // ============ 第二步：城市信息匹配 ============
        Map<String, String> cityMap = areaCacheTool.getCityToProvinceMap();
        for (String city : areaCacheTool.getAllCityNames()) {
            if (unitName.contains(city)) {
                String province = cityMap.get(city);
                System.out.println("单位名称匹配到城市全称: " + unitName + " → " + city + " → " + province);
                return province;
            }
        }

        for (String city : areaCacheTool.getAllCityNames()) {
            String cityAbbr = getCityAbbreviation(city);
            if (hasValue(cityAbbr) && unitName.contains(cityAbbr)) {
                String province = cityMap.get(city);
                System.out.println("单位名称匹配到城市简写: " + unitName + " → " + cityAbbr + " → " + city + " → " + province);
                return province;
            }
        }

        // ============ 第三步：省份信息匹配 ============
        for (String province : areaCacheTool.getAllProvinceNames()) {
            if (unitName.contains(province)) {
                System.out.println("单位名称匹配到省份全称: " + unitName + " → " + province);
                return province;
            }
        }

        for (String province : areaCacheTool.getAllProvinceNames()) {
            String provinceAbbr = getProvinceAbbreviation(province);
            if (hasValue(provinceAbbr) && unitName.contains(provinceAbbr)) {
                System.out.println("单位名称匹配到省份简写: " + unitName + " → " + provinceAbbr + " → " + province);
                return province;
            }
        }

        // ============ 第四步：战区信息匹配 ============
        for (String warZone : WAR_ZONE_MAPPING.keySet()) {
            if (unitName.contains(warZone)) {
                String[] provinceCity = WAR_ZONE_MAPPING.get(warZone).split("-");
                System.out.println("单位名称匹配到战区: " + unitName + " → " + warZone + " → " + provinceCity[0]);
                return provinceCity[0];
            }
        }

        // ============ 第五步：都无结果 → 返回"未知" ============
        System.out.println("单位名称未匹配到任何省市: " + unitName);
        return "未知";
    }

    /**
     * 辅助6：刷新所有资产表状态（核心修正）
     * 重要修正：无论三类资产以什么顺序导入，都能确保每个资产表的状态正确
     * - 每次操作都重新检查所有资产表的状态，确保状态准确反映实际数据
     * - 状态更新基于实际数据存在性，不受导入顺序影响
     *
     * @param reportUnit 上报单位实体
     * @param unitName 上报单位名称
     */
    private void refreshAllAssetStatus(ReportUnit reportUnit, String unitName) {
        // 检查软件资产是否有数据
        long softwareCount = countAssetByUnit(unitName, "software");
        short softwareStatus = softwareCount > 0 ? (short) 1 : (short) 0;
        reportUnit.setSource_table_software_asset(softwareStatus);

        // 检查网信资产是否有数据
        long cyberCount = countAssetByUnit(unitName, "cyber");
        short cyberStatus = cyberCount > 0 ? (short) 1 : (short) 0;
        reportUnit.setSource_table_cyber_asset(cyberStatus);

        // 检查数据内容资产是否有数据
        long dataContentCount = countAssetByUnit(unitName, "dataContent");
        short dataContentStatus = dataContentCount > 0 ? (short) 1 : (short) 0;
        reportUnit.setSource_table_data_content_asset(dataContentStatus);

        System.out.println("刷新所有资产表状态：" + unitName +
                " [软件表:" + softwareCount + "条=" + softwareStatus +
                ", 网信表:" + cyberCount + "条=" + cyberStatus +
                ", 数据表:" + dataContentCount + "条=" + dataContentStatus + "]");
    }

    /**
     * 辅助7：标准化省份名称
     * 修正：将"四川"统一为"四川省"，"北京"统一为"北京市"等
     * 确保所有省份名称使用标准格式，便于后续筛选和统计
     *
     * @param provinceName 原始省份名称
     * @return 标准化后的省份名称
     */
    private String standardizeProvinceName(String provinceName) {
        if (!hasValue(provinceName)) {
            return provinceName;
        }

        // 检查是否是标准省份名称
        for (String standardProvince : areaCacheTool.getAllProvinceNames()) {
            if (standardProvince.equals(provinceName)) {
                return provinceName; // 已经是标准格式
            }
        }

        // 尝试匹配简称到标准名称
        for (String standardProvince : areaCacheTool.getAllProvinceNames()) {
            if (standardProvince.contains(provinceName) && !provinceName.equals("未知")) {
                System.out.println("省份简称匹配: '" + provinceName + "' -> '" + standardProvince + "'");
                return standardProvince;
            }
        }

        return provinceName; // 无法标准化，返回原值
    }

    /**
     * 辅助8：标准化城市名称
     * 修正：将城市名称统一为标准格式，确保与自动填充内容一致
     *
     * @param cityName 原始城市名称
     * @return 标准化后的城市名称
     */
    private String standardizeCityName(String cityName) {
        if (!hasValue(cityName)) {
            return cityName;
        }

        // 检查是否是标准城市名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            if (standardCity.equals(cityName)) {
                return cityName; // 已经是标准格式
            }
        }

        // 尝试匹配简称到标准名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            if (standardCity.contains(cityName)) {
                System.out.println("城市简称匹配: '" + cityName + "' -> '" + standardCity + "'");
                return standardCity;
            }
        }

        return cityName; // 无法标准化，返回原值
    }

    // ============================ 新增：省市推导辅助方法 ============================

    /**
     * 获取省份名称的简写形式

     * 功能说明：
     * - 移除省份名称中的行政区划后缀，便于简写匹配
     * - 例如："江苏省" → "江苏"，"北京市" → "北京"
     * - 支持所有类型的省级行政区划名称标准化

     * 处理规则：
     * 1. 移除"省"后缀：江苏省 → 江苏
     * 2. 移除"自治区"后缀：内蒙古自治区 → 内蒙古
     * 3. 移除"壮族自治区"等民族自治区后缀：广西壮族自治区 → 广西
     * 4. 移除"特别行政区"后缀：香港特别行政区 → 香港
     * 5. 移除"市"后缀（处理直辖市）：北京市 → 北京

     * 应用场景：
     * - 在单位名称中匹配省份简写（如"江苏军区"）
     * - 提高省市自动推导的覆盖率和准确性
     * - 支持用户习惯的简写输入方式
     *
     * @param province 完整的省份名称
     * @return 去除后缀的省份简写名称
     */
    private String getProvinceAbbreviation(String province) {
        return province.replace("省", "")
                .replace("自治区", "")
                .replace("壮族自治区", "")
                .replace("维吾尔自治区", "")
                .replace("回族自治区", "")
                .replace("特别行政区", "")
                .replace("市", ""); // 处理直辖市
    }

    /**
     * 获取城市名称的简写形式

     * 功能说明：
     * - 移除城市名称中的"市"后缀，便于简写匹配
     * - 例如："南京市" → "南京"，"广州市" → "广州"
     * - 支持所有地级市名称的标准化处理

     * 处理规则：
     * - 简单移除"市"后缀，保留城市核心名称
     * - 不处理特殊的行政区划（如自治州、地区等）

     * 应用场景：
     * - 在单位名称中匹配城市简写（如"南京军区"）
     * - 提高城市名称的识别覆盖率
     * - 适应不同用户的命名习惯

     * 注意事项：
     * - 直辖市（北京、上海等）也会被处理，但不影响使用
     * - 自治州、地区等特殊行政区划不在处理范围内
     * @param city 完整的城市名称
     * @return 去除"市"后缀的城市简写名称
     */
    private String getCityAbbreviation(String city) {
        return city.replace("市", "");
    }

    /**
     * 县级单位推导逻辑

     * 功能说明：
     * - 根据单位名称中是否包含县级单位名称，推导对应的省市信息
     * - 通过预定义的映射表，将县级单位关联到对应的地级市和省份
     * - 为基层单位提供准确的省市自动推导

     * 设计理念：
     * 1. 覆盖常见县级单位：主要包含江苏和广东的县级单位
     * 2. 映射关系固定：县级单位 → 省份-城市 的固定映射
     * 3. 可扩展性强：通过维护映射表轻松扩展支持的县级单位

     * 映射表结构：
     * - Key：县级单位名称（如"昆山县"、"南海县"）
     * - Value：省份-城市组合（如"江苏省-苏州市"、"广东省-佛山市"）

     * 应用场景：
     * - 单位名称包含区县级信息（如"昆山县人武部"）
     * - 基层单位的省市信息自动填充
     * - 提高县级单位的识别准确率

     * 扩展方法：
     * - 在countyMapping映射表中添加新的县级单位
     * - 格式："县级单位名称" -> "省份-城市"
     * - 例如：countyMapping.put("新县", "江苏省-南京市");

     * 当前覆盖范围：
     * - 江苏省带"县"字的县级单位
     * - 广东省带"县"字的县级单位
     *
     * @param unitName 上报单位名称
     * @return 对应的省市组合字符串（格式："省份-城市"），如无匹配返回null
     */
    private String deriveFromCounty(String unitName) {
        // 县级单位映射表：县级单位名称 -> 省份-城市
        Map<String, String> countyMapping = new HashMap<>();

        // ============ 江苏省带"县"字的县级单位 ============
        // 淮安市
        countyMapping.put("涟水县", "江苏省-淮安市");
        countyMapping.put("洪泽县", "江苏省-淮安市");
        countyMapping.put("盱眙县", "江苏省-淮安市");
        countyMapping.put("金湖县", "江苏省-淮安市");

        // 连云港市
        countyMapping.put("东海县", "江苏省-连云港市");
        countyMapping.put("灌云县", "江苏省-连云港市");
        countyMapping.put("灌南县", "江苏省-连云港市");

        // 宿迁市
        countyMapping.put("沭阳县", "江苏省-宿迁市");
        countyMapping.put("泗阳县", "江苏省-宿迁市");
        countyMapping.put("泗洪县", "江苏省-宿迁市");

        // 盐城市
        countyMapping.put("响水县", "江苏省-盐城市");
        countyMapping.put("滨海县", "江苏省-盐城市");
        countyMapping.put("阜宁县", "江苏省-盐城市");
        countyMapping.put("射阳县", "江苏省-盐城市");
        countyMapping.put("建湖县", "江苏省-盐城市");

        // 徐州市
        countyMapping.put("丰县", "江苏省-徐州市");
        countyMapping.put("沛县", "江苏省-徐州市");
        countyMapping.put("睢宁县", "江苏省-徐州市");

        // 南通市
        countyMapping.put("海安县", "江苏省-南通市");
        countyMapping.put("如东县", "江苏省-南通市");

        // 扬州市
        countyMapping.put("宝应县", "江苏省-扬州市");

        // 泰州市
        countyMapping.put("兴化县", "江苏省-泰州市");
        countyMapping.put("靖江县", "江苏省-泰州市");
        countyMapping.put("泰兴县", "江苏省-泰州市");
        countyMapping.put("姜堰县", "江苏省-泰州市");

        // 镇江市
        countyMapping.put("丹徒县", "江苏省-镇江市");
        countyMapping.put("句容县", "江苏省-镇江市");
        countyMapping.put("扬中县", "江苏省-镇江市");

        // ============ 广东省带"县"字的县级单位 ============
        // 清远市
        countyMapping.put("佛冈县", "广东省-清远市");
        countyMapping.put("阳山县", "广东省-清远市");
        countyMapping.put("连山壮族瑶族自治县", "广东省-清远市");
        countyMapping.put("连南瑶族自治县", "广东省-清远市");

        // 韶关市
        countyMapping.put("始兴县", "广东省-韶关市");
        countyMapping.put("仁化县", "广东省-韶关市");
        countyMapping.put("翁源县", "广东省-韶关市");
        countyMapping.put("乳源瑶族自治县", "广东省-韶关市");
        countyMapping.put("新丰县", "广东省-韶关市");

        // 梅州市
        countyMapping.put("大埔县", "广东省-梅州市");
        countyMapping.put("丰顺县", "广东省-梅州市");
        countyMapping.put("五华县", "广东省-梅州市");
        countyMapping.put("平远县", "广东省-梅州市");
        countyMapping.put("蕉岭县", "广东省-梅州市");

        // 汕尾市
        countyMapping.put("海丰县", "广东省-汕尾市");
        countyMapping.put("陆河县", "广东省-汕尾市");

        // 河源市
        countyMapping.put("紫金县", "广东省-河源市");
        countyMapping.put("龙川县", "广东省-河源市");
        countyMapping.put("连平县", "广东省-河源市");
        countyMapping.put("和平县", "广东省-河源市");
        countyMapping.put("东源县", "广东省-河源市");

        // 阳江市
        countyMapping.put("阳西县", "广东省-阳江市");
        countyMapping.put("阳东县", "广东省-阳江市");

        // 湛江市
        countyMapping.put("遂溪县", "广东省-湛江市");
        countyMapping.put("徐闻县", "广东省-湛江市");

        // 茂名市
        countyMapping.put("电白县", "广东省-茂名市");

        // 肇庆市
        countyMapping.put("广宁县", "广东省-肇庆市");
        countyMapping.put("怀集县", "广东省-肇庆市");
        countyMapping.put("封开县", "广东省-肇庆市");
        countyMapping.put("德庆县", "广东省-肇庆市");

        // 惠州市
        countyMapping.put("博罗县", "广东省-惠州市");
        countyMapping.put("惠东县", "广东省-惠州市");
        countyMapping.put("龙门县", "广东省-惠州市");

        // 汕头市
        countyMapping.put("南澳县", "广东省-汕头市");

        // 揭阳市
        countyMapping.put("惠来县", "广东省-揭阳市");
        countyMapping.put("揭西县", "广东省-揭阳市");

        // 潮州市
        countyMapping.put("饶平县", "广东省-潮州市");

        // 遍历映射表，查找匹配的县级单位
        for (String county : countyMapping.keySet()) {
            if (unitName.contains(county)) {
                return countyMapping.get(county);
            }
        }
        return null;
    }

    /**
     * 🆕 新增：清理三个状态都为0的上报单位记录
     * 🎯 作用：自动清理无效数据，保持数据库整洁
     * 💡 触发条件：三个状态字段都为0时自动删除
     */
    private void cleanupZeroStatusRecords() {
        try {
            // 查找所有三个状态都为0的记录
            java.util.List<ReportUnit> zeroStatusUnits = reportUnitMapper.selectAllZeroStatusUnits();
            if (zeroStatusUnits != null && !zeroStatusUnits.isEmpty()) {
                System.out.println("🗑️ 开始清理无效上报单位记录，数量: " + zeroStatusUnits.size());
                for (ReportUnit unit : zeroStatusUnits) {
                    reportUnitMapper.deleteById(unit.getId());
                    System.out.println("✅ 已删除无效上报单位: " + unit.getReportUnit());
                }
                System.out.println("✅ 自动清理完成，共删除 " + zeroStatusUnits.size() + " 个无效上报单位记录");
            }
        } catch (Exception e) {
            System.err.println("❌ 清理无效记录时出错: " + e.getMessage());
            e.printStackTrace(); // 🆕 添加详细错误信息
        }
    }


    // ============================ 🆕 新增方法（批量同步专用） ============================

    /**
     * 批量同步请求内部类
     * 🎯 作用：封装批量同步所需的参数，便于统一处理
     * 💡 使用场景：清空再导入时，批量同步上报单位表状态
     */
    @Getter
    @AllArgsConstructor
    public static class UnitSyncRequest {
        private final String unitName;
        private final String province;
        private final String assetType;
        private final boolean isDelete;
    }

    /**
     * 批量同步上报单位（性能优化专用）
     * 🎯 作用：减少数据库事务开销，提高批量导入性能
     * 💡 优化特性：
     * - 合并多个单位的同步操作
     * - 减少数据库连接次数
     * - 批量提交提高性能
     * - 相同单位只处理一次

     * 🔧 使用场景：
     * - 软件资产批量导入（省份为null，自动推导）
     * - 网信/数据资产批量导入（使用Excel中的省市）
     * - 批量删除操作
     *
     * @param unitSyncRequests 批量同步请求列表
     */
    public void batchSyncReportUnits(java.util.List<UnitSyncRequest> unitSyncRequests) {
        if (unitSyncRequests == null || unitSyncRequests.isEmpty()) {
            System.out.println("批量同步：无请求需要处理");
            return;
        }

        System.out.println("🔄 开始批量同步上报单位，数量: " + unitSyncRequests.size());

        // ============ 按单位名称分组，合并相同单位的请求 ============
        java.util.Map<String, UnitSyncRequest> mergedRequests = new java.util.HashMap<>();
        for (UnitSyncRequest request : unitSyncRequests) {
            String unitName = request.getUnitName();
            if (request.isDelete()) {
                mergedRequests.put(unitName, request);
            } else if (!mergedRequests.containsKey(unitName)) {
                mergedRequests.put(unitName, request);
            }
        }

        System.out.println("📊 合并后单位数量: " + mergedRequests.size());

        // ============ 批量处理每个单位的同步 ============
        int successCount = 0;
        int errorCount = 0;

        for (UnitSyncRequest request : mergedRequests.values()) {
            try {
                // 🆕 直接调用非静态方法（因为 batchSyncReportUnits 本身也是非静态的）
                syncReportUnit(
                        request.getUnitName(),
                        request.getProvince(),
                        request.getAssetType(),
                        request.isDelete()
                );
                successCount++;
            } catch (Exception e) {
                errorCount++;
                System.err.println("❌ 批量同步失败 - 单位: " + request.getUnitName() + ", 错误: " + e.getMessage());
            }
        }

        System.out.println("✅ 批量同步上报单位完成 - 成功: " + successCount + "个, 失败: " + errorCount + "个");
        // ============ 🆕 新增：批量操作后清理所有无效记录 ============
        cleanupZeroStatusRecords();
    }
}