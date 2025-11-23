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
 *        a. 标准化处理：统一省名称格式
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
 * 3. 格式标准化：统一省名称格式，避免数据不一致
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

 * ==================== 上报单位表的自动清理机制 -- cleanupZeroStatusRecords方法 ====================
 * 本工具提供自动清理无效上报单位记录的功能，确保数据库数据的精简高效。

 * 清理条件：当上报单位在三个资产表中的状态标志均为0时（表示无数据），
 *           系统会自动删除该记录，避免数据冗余。

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

        // ============ 省份标准化处理 ============
        // 确保Excel导入和自动填充使用相同的标准格式，避免"四川"和"四川省"同时出现
        if (hasValue(excelProvince)) {
            String standardizedProvince = standardizeProvinceName(excelProvince);
            if (!excelProvince.equals(standardizedProvince)) {
                System.out.println("省份标准化: '" + excelProvince + "' -> '" + standardizedProvince + "'");
                excelProvince = standardizedProvince;
                asset.setProvince(standardizedProvince);
            }
        }
        // ============ 🆕 新增：城市标准化处理 ============
        // 确保Excel导入和自动填充使用相同的标准格式，避免"南京"和"南京市"同时出现
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
            asset.setCity("未知");
            System.out.println("单位名称为空，设置为默认值: 未知-未知");
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
        asset.setCity("未知");
        System.out.println("第五步：所有匹配规则都失败，设置为默认值: 未知-未知");
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
     * 辅助7：标准化省份名称（精简版本）
     * 只处理省份标准化，不处理城市
     *
     * @param provinceName 原始省份名称
     * @return 标准化后的省份名称
     */
    private String standardizeProvinceName(String provinceName) {
        if (!hasValue(provinceName)) {
            return provinceName;
        }

        provinceName = provinceName.trim();

        // 1. 检查是否已经是标准省份名称
        for (String standardProvince : areaCacheTool.getAllProvinceNames()) {
            if (standardProvince.equals(provinceName)) {
                return provinceName; // 已经是标准格式
            }
        }

        // 2. 精确的简称到全称映射
        Map<String, String> provinceMapping = createProvinceMapping();

        if (provinceMapping.containsKey(provinceName)) {
            String standardized = provinceMapping.get(provinceName);
            System.out.println("省份标准化: '" + provinceName + "' -> '" + standardized + "'");
            return standardized;
        }

        // 3. 处理冗余后缀（更清晰的写法）
        String cleaned = removeRedundantSuffix(provinceName);
        if (!cleaned.equals(provinceName)) {
            // 检查修正后的名称是否标准
            for (String standardProvince : areaCacheTool.getAllProvinceNames()) {
                if (standardProvince.equals(cleaned)) {
                    System.out.println("省份后缀修正: '" + provinceName + "' -> '" + cleaned + "'");
                    return cleaned;
                }
            }
        }

        System.out.println("无法标准化省份: '" + provinceName + "'，保持原值");
        return provinceName;
    }
    /**
     * 创建省份简称到全称的映射表
     */
    private Map<String, String> createProvinceMapping() {
        Map<String, String> mapping = new HashMap<>();

        // 直辖市和自治区
        mapping.put("北京", "北京市");
        mapping.put("上海", "上海市");
        mapping.put("天津", "天津市");
        mapping.put("重庆", "重庆市");
        mapping.put("新疆", "新疆维吾尔自治区");
        mapping.put("广西", "广西壮族自治区");
        mapping.put("宁夏", "宁夏回族自治区");
        mapping.put("西藏", "西藏自治区");
        mapping.put("内蒙古", "内蒙古自治区");

        // 普通省份
        mapping.put("黑龙江", "黑龙江省");
        mapping.put("吉林", "吉林省");
        mapping.put("辽宁", "辽宁省");
        mapping.put("河北", "河北省");
        mapping.put("河南", "河南省");
        mapping.put("山东", "山东省");
        mapping.put("山西", "山西省");
        mapping.put("江苏", "江苏省");
        mapping.put("浙江", "浙江省");
        mapping.put("安徽", "安徽省");
        mapping.put("福建", "福建省");
        mapping.put("江西", "江西省");
        mapping.put("湖北", "湖北省");
        mapping.put("湖南", "湖南省");
        mapping.put("广东", "广东省");
        mapping.put("海南", "海南省");
        mapping.put("四川", "四川省");
        mapping.put("贵州", "贵州省");
        mapping.put("云南", "云南省");
        mapping.put("陕西", "陕西省");
        mapping.put("甘肃", "甘肃省");
        mapping.put("青海", "青海省");

        return mapping;
    }
    /**
     * 去除冗余后缀（更清晰的实现）
     */
    private String removeRedundantSuffix(String provinceName) {
        // 检查是否有冗余后缀
        if (provinceName.endsWith("省省")) {
            return provinceName.substring(0, provinceName.length() - 1); // 去掉最后一个"省"
        }
        if (provinceName.endsWith("市市")) {
            return provinceName.substring(0, provinceName.length() - 1); // 去掉最后一个"市"
        }
        if (provinceName.endsWith("自治区区")) {
            return provinceName.substring(0, provinceName.length() - 1); // 去掉最后一个"区"
        }

        return provinceName; // 没有冗余后缀，返回原值
    }

    // ============================ 🆕 新增：城市标准化方法 ============================
    /**
     * 🏷️ 城市名称标准化（完整版）
     *
     * ==================== 方法说明 ====================
     * 对城市名称进行完整的标准化处理，支持多种行政区划类型的标准化。
     * 包括地级市、县级市、自治州、地区、盟、特别行政区等。
     *
     * ==================== 标准化规则 ====================
     * 1. 精确匹配：检查是否已经是标准城市名称
     * 2. 简写匹配：使用城市简写进行匹配
     * 3. 包含匹配：检查标准城市名称是否包含输入的城市名称（兜底方案）
     *
     * ==================== 技术实现 ====================
     * - 优先使用精确匹配，确保准确性
     * - 支持简写匹配，提高用户输入容错性
     * - 完整的日志记录，便于问题排查
     * - 与省份标准化保持一致的逻辑结构
     *
     * ==================== 应用场景 ====================
     * - Excel导入时的城市名称标准化
     * - 用户手动输入的城市名称规范化
     * - 确保省市数据格式的统一性
     *
     * @param cityName 原始城市名称
     * @return 标准化后的城市名称
     *
     * @apiNote 此方法与省份标准化配合使用，确保完整的省市信息规范性
     */
    private String standardizeCityName(String cityName) {
        // 1. 空值检查：确保输入有效
        if (!hasValue(cityName)) {
            return cityName;
        }

        cityName = cityName.trim();

        // 2. 检查是否已经是标准城市名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            if (standardCity.equals(cityName)) {
                return cityName; // 已经是标准格式，直接返回
            }
        }

        // 3. 简写匹配：使用城市简写进行匹配
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            String cityAbbr = getCityAbbreviation(standardCity);
            if (cityAbbr.equals(cityName)) {
                System.out.println("🏷️ 城市简写匹配: '" + cityName + "' → '" + standardCity + "'");
                return standardCity;
            }
        }

        // 4. 包含匹配（兜底方案）：检查标准城市名称是否包含输入的城市名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            if (standardCity.contains(cityName)) {
                System.out.println("🏷️ 城市包含匹配: '" + cityName + "' → '" + standardCity + "'");
                return standardCity;
            }
        }

        // 5. 无法标准化的情况：返回原名称并记录日志
        System.out.println("⚠️ 无法标准化城市名称: '" + cityName + "'，保持原值");
        return cityName;
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

     * ==================== 功能说明 ====================
     * 本方法用于从完整的行政区划名称中提取核心简写名称，便于在单位名称中进行匹配。
     * 支持处理所有类型的行政区划，包括地级市、县级市、自治州、地区、盟、特别行政区等。

     * ==================== 处理规则 ====================
     * 1. 特殊自治州映射：对常见自治州使用习惯简写（如"湘西土家族苗族自治州"→"湘西"）
     * 2. 后缀去除规则：按行政区划类型去除相应后缀
     *    - 市：去除"市"后缀（"南京市"→"南京"）
     *    - 自治州：去除"自治州"后缀（"阿坝藏族羌族自治州"→"阿坝"）
     *    - 地区：去除"地区"后缀（"大兴安岭地区"→"大兴安岭"）
     *    - 盟：去除"盟"后缀（"兴安盟"→"兴安"）
     *    - 特别行政区：去除"特别行政区"后缀（"香港特别行政区"→"香港"）

     * ==================== 设计理念 ====================
     * 1. 准确性优先：确保简写能够准确代表原行政区划
     * 2. 用户习惯：采用符合日常使用习惯的简写形式
     * 3. 匹配友好：生成的简写便于在单位名称中进行字符串包含匹配
     * 4. 全面覆盖：支持所有类型的行政区划名称处理

     * ==================== 应用场景 ====================
     * 场景1：单位名称匹配
     *   - 在"南京军区"中匹配"南京"对应"南京市"
     *   - 在"湘西军分区"中匹配"湘西"对应"湘西土家族苗族自治州"

     * 场景2：数据标准化
     *   - 统一不同行政区划类型的简写格式
     *   - 为省市自动填充提供准确的匹配基础

     * 场景3：用户输入适配
     *   - 支持用户习惯的简写输入方式
     *   - 提高省市自动推导的覆盖率和准确性

     * ==================== 返回值说明 ====================
     * - 成功处理：返回去除后缀的核心名称（如"南京"、"湘西"）
     * - 空值输入：原样返回空值
     * - 无法处理：返回原名称（兜底处理）

     * ==================== 示例说明 ====================
     * 输入："南京市"          → 输出："南京"
     * 输入："湘西土家族苗族自治州" → 输出："湘西"
     * 输入："大兴安岭地区"      → 输出："大兴安岭"
     * 输入："兴安盟"          → 输出："兴安"
     * 输入："香港特别行政区"    → 输出："香港"
     * 输入："定州市"          → 输出："定州"
     *
     * @param city 完整的城市/行政区划名称
     * @return 处理后的简写名称，如无法处理则返回原名称
     */
    private String getCityAbbreviation(String city) {
        // 1. 空值检查：确保输入有效
        if (!hasValue(city)) {
            return city;
        }

        // 2. 特殊自治州映射：对常见自治州使用习惯简写
        Map<String, String> specialAutonomousMapping = new HashMap<>();
        specialAutonomousMapping.put("湘西土家族苗族自治州", "湘西");
        specialAutonomousMapping.put("延边朝鲜族自治州", "延边");
        specialAutonomousMapping.put("恩施土家族苗族自治州", "恩施");
        specialAutonomousMapping.put("阿坝藏族羌族自治州", "阿坝");
        specialAutonomousMapping.put("甘孜藏族自治州", "甘孜");
        specialAutonomousMapping.put("凉山彝族自治州", "凉山");
        specialAutonomousMapping.put("黔西南布依族苗族自治州", "黔西南");
        specialAutonomousMapping.put("黔东南苗族侗族自治州", "黔东南");
        specialAutonomousMapping.put("黔南布依族苗族自治州", "黔南");
        specialAutonomousMapping.put("楚雄彝族自治州", "楚雄");
        specialAutonomousMapping.put("红河哈尼族彝族自治州", "红河");
        specialAutonomousMapping.put("文山壮族苗族自治州", "文山");
        specialAutonomousMapping.put("西双版纳傣族自治州", "西双版纳");
        specialAutonomousMapping.put("大理白族自治州", "大理");
        specialAutonomousMapping.put("德宏傣族景颇族自治州", "德宏");
        specialAutonomousMapping.put("怒江傈僳族自治州", "怒江");
        specialAutonomousMapping.put("迪庆藏族自治州", "迪庆");
        specialAutonomousMapping.put("临夏回族自治州", "临夏");
        specialAutonomousMapping.put("甘南藏族自治州", "甘南");
        specialAutonomousMapping.put("海北藏族自治州", "海北");
        specialAutonomousMapping.put("黄南藏族自治州", "黄南");
        specialAutonomousMapping.put("海南藏族自治州", "海南");
        specialAutonomousMapping.put("果洛藏族自治州", "果洛");
        specialAutonomousMapping.put("玉树藏族自治州", "玉树");
        specialAutonomousMapping.put("海西蒙古族藏族自治州", "海西");

        // 检查特殊映射
        if (specialAutonomousMapping.containsKey(city)) {
            String abbreviation = specialAutonomousMapping.get(city);
            // 🗑️ 移除冗余日志：不再输出每个特殊自治州的映射
//          System.out.println("特殊自治州简写映射: '" + city + "' -> '" + abbreviation + "'");
            return abbreviation;
        }

        // 3. 常规后缀处理：按行政区划类型去除相应后缀
        // 注意：按后缀长度从长到短处理，避免错误匹配

        // 3.1 特别行政区处理
        if (city.endsWith("特别行政区")) {
            return city.replace("特别行政区", "");
        }

        // 3.2 自治州处理（兜底，处理不在特殊映射中的自治州）
        if (city.endsWith("自治州")) {
            return city.replace("自治州", "");
        }

        // 3.3 地区处理
        if (city.endsWith("地区")) {
            return city.replace("地区", "");
        }

        // 3.4 盟处理
        if (city.endsWith("盟")) {
            return city.replace("盟", "");
        }

        // 3.5 市处理（最后处理，因为"市"可能出现在其他类型中）
        if (city.endsWith("市")) {
            return city.replace("市", "");
        }

        // 4. 无法处理的情况：返回原名称
        System.out.println("无法简写的城市名称: '" + city + "'，保持原值");
        return city;
    }

    /**
     * 县级单位推导逻辑（基于AreaCacheTool缓存）

     * ==================== 功能说明 ====================
     * 本方法通过查询AreaCacheTool中的县级单位映射缓存，根据单位名称中是否包含县级单位名称，
     * 推导对应的省市信息。所有县级单位映射关系通过配置文件管理，支持动态扩展。

     * ==================== 数据来源 ====================
     * - 主数据源：classpath:province/county-mapping.json
     * - 缓存管理：AreaCacheTool.countyToProvinceCityMap
     * - 映射格式：县级单位名称 → "省份-城市"（如"涟水县" → "江苏省-淮安市"）

     * ==================== 匹配逻辑 ====================
     * 1. 遍历县级单位映射表中的所有县级单位名称
     * 2. 检查上报单位名称是否包含任一县级单位名称
     * 3. 使用首次匹配到的县级单位对应的省市信息
     * 4. 如无匹配则返回null，由上级调用方法继续其他推导方式

     * ==================== 设计理念 ====================
     * 1. 配置化管理：所有县级映射关系在JSON文件中配置，无需修改代码
     * 2. 优先级最高：县级单位匹配具有最高优先级，确保基层单位准确识别
     * 3. 扩展性强：新增县级单位只需在配置文件中添加，自动生效
     * 4. 性能优化：基于内存缓存，查询操作O(n)时间复杂度

     * ==================== 应用场景 ====================
     * 场景1：基层单位识别
     *   - 识别包含县级单位名称的上报单位（如"涟水县人武部"）
     *   - 为县级及以下单位提供准确的省市自动填充

     * 场景2：数据标准化
     *   - 统一县级单位的省市归属关系
     *   - 避免同一县级单位在不同记录中的省市不一致

     * 场景3：扩展维护
     *   - 新增县级单位时无需修改代码
     *   - 支持批量添加和修改县级映射关系

     * ==================== 扩展方法 ====================
     * 在county-mapping.json中添加新的县级单位：
     * {
     *   "countyMapping": {
     *     "省份名称": {
     *       "城市名称": ["县级单位1", "县级单位2", ...]
     *     }
     *   }
     * }

     * 示例：添加江苏省南京市的县级单位
     * "江苏省": {
     *   "南京市": ["新县1", "新县2"]
     * }

     * ==================== 返回值说明 ====================
     * - 成功匹配：返回"省份-城市"格式字符串（如"江苏省-淮安市"）
     * - 无匹配：返回null，由调用方继续其他推导方式
     * - 异常情况：返回null，确保不影响主流程
     *
     * @param unitName 上报单位名称
     * @return 对应的省市组合字符串（格式："省份-城市"），如无匹配返回null
     */
    private String deriveFromCounty(String unitName) {
        // 🆕 从AreaCacheTool获取县级映射表
        Map<String, String> countyMapping = areaCacheTool.getCountyToProvinceCityMap();

        if (countyMapping == null || countyMapping.isEmpty()) {
            System.out.println("⚠️ 县级单位映射表为空，跳过县级匹配");
            return null;
        }

        System.out.println("🔍 正在匹配县级单位，共" + countyMapping.size() + "个县级单位");

        // 遍历映射表，查找匹配的县级单位
        for (String county : countyMapping.keySet()) {
            if (unitName.contains(county)) {
                String provinceCity = countyMapping.get(county);
                System.out.println("✅ 匹配到县级单位: " + county + " → " + provinceCity);
                return provinceCity;
            }
        }

        System.out.println("❌ 未匹配到任何县级单位");
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