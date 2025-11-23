package com.military.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.military.asset.entity.DataContentAsset;
import com.military.asset.mapper.DataContentAssetMapper;
import com.military.asset.service.DataContentAssetService;
import com.military.asset.utils.CategoryMapUtils;
import com.military.asset.utils.ProvinceAutoFillTool; // 新增：导入自动填充工具
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 确保导入Page类
import com.military.asset.utils.DataContentAssetMetricsUtils;
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.excel.DataContentAssetExcelVO;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource; // 新增：资源注入注解
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collections;


//导出功能依赖
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
// 添加这行导入
import org.springframework.beans.factory.annotation.Autowired;

//修改导入依赖
import com.military.asset.entity.CyberAsset;
import com.military.asset.entity.ReportUnit;
import com.military.asset.entity.HasReportUnitAndProvince;
import com.military.asset.mapper.CyberAssetMapper;
import com.military.asset.mapper.ReportUnitMapper;
import com.military.asset.utils.AreaCacheTool;




/**
 * 数据内容资产业务实现类
 * 适配数据特有约束（开发工具非空），结构与软件/网信资产保持一致
 * - getExistingAssetsMap(): 实现完整资产对象Map的加载，用于导入时关键字段比较

 * 新增功能：
 * - 省市自动填充：集成ProvinceAutoFillTool实现省市字段自动填充
 * - 上报单位表同步：在增删改操作中同步上报单位表状态
 */
@Service
@Slf4j
@SuppressWarnings("unused")
public class DataContentAssetServiceImpl extends ServiceImpl<DataContentAssetMapper, DataContentAsset> implements DataContentAssetService {


    /**
     * 数据资产分类映射表：从工具类获取标准编码-分类对应关系
     */
    private final Map<String, String> CATEGORY_MAP = CategoryMapUtils.initDataCategoryMap();
    // ============================ 新增依赖注入 ============================

    /**
     * 省市自动填充工具：负责处理省市字段的自动填充逻辑
     * 支持场景：Excel有值优先、填省补首府、填市补省、修改上报单位同步
     */
    @Resource
    private ProvinceAutoFillTool provinceAutoFillTool;

    /**
     * 数据内容资产数据访问接口
     * 用于执行数据内容资产表的数据库操作，包括自定义查询和统计
     * 通过Spring依赖注入自动装配，确保单例性和线程安全
     */
    @Autowired
    private DataContentAssetMapper dataContentAssetMapper;

    // ==================== 依赖注入区域 ====================

    /**
     * 区域缓存工具：提供省市字典数据、首府查询、城市到省份映射等核心功能
     * 用于智能推导和标准化省市信息，确保省市数据的准确性和一致性
     */
    @Resource
    private AreaCacheTool areaCacheTool;

    /**
     * 上报单位表Mapper：操作report_unit表，用于维护上报单位的状态和省市信息
     * 提供单位查询、状态统计等核心数据库操作，支撑上报单位表的智能同步
     */
    @Resource
    private ReportUnitMapper reportUnitMapper;

    /**
     * 网信资产表Mapper：用于跨表同步操作
     * 当数据资产的省市变更时，同步更新网信资产表中相同单位的省市信息
     * 确保同一单位在不同资产表中的省市信息保持一致
     */
    @Resource
    private CyberAssetMapper cyberAssetMapper;

    // ============================ 新增方法实现 ============================

    @Override
    public Map<String, DataContentAsset> getExistingAssetsMap() {
        try {
            // 查询所有已存在的数据内容资产（完整对象）
            List<DataContentAsset> existingAssets = baseMapper.selectAllExistingAssets();

            // 转换为Map结构，键为资产ID，值为完整资产对象
            // 使用Collectors.toMap提供O(1)的查询性能
            Map<String, DataContentAsset> assetsMap = existingAssets.stream()
                    .collect(Collectors.toMap(
                            DataContentAsset::getId,  // 键：资产ID
                            asset -> asset,          // 值：完整资产对象
                            (existing, replacement) -> existing  // 冲突处理：保留现有值
                    ));

            log.info("成功加载{}条数据内容资产到内存Map，用于导入时关键字段比较", assetsMap.size());
            return assetsMap;

        } catch (Exception e) {
            log.error("加载数据内容资产Map失败，无法进行关键字段比较", e);
            throw new RuntimeException("加载资产数据失败: " + e.getMessage());
        }
    }

    // ============================ 原有方法实现（保持业务逻辑不变） ============================


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveDataContentAssets(List<DataContentAssetExcelVO> validDataList) {
        // 调用原有的 batchSaveValidData 方法
        batchSaveValidData(validDataList);
    }

    @Override
    public List<String> getExistingIds() {
        try {
            List<String> ids = baseMapper.selectAllExistingIds();
            log.info("查询数据资产已存在ID完成，共{}条记录", ids.size());
            return ids;
        } catch (Exception e) {
            log.error("查询数据资产ID失败", e);
            throw new RuntimeException("查询ID异常：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveValidData(List<DataContentAssetExcelVO> validVoList) {
        if (validVoList.isEmpty()) {
            log.info("无合法数据资产需保存");
            return;
        }

        List<DataContentAsset> entities = new ArrayList<>();
        for (DataContentAssetExcelVO vo : validVoList) {
            DataContentAsset entity = new DataContentAsset();
            BeanUtils.copyProperties(vo, entity);
            entity.setCreateTime(LocalDateTime.now());

            // ============ 新增：省市自动填充（Excel导入场景） ============
            // 调用自动填充工具，isUpdate=false表示Excel导入场景
            // 处理逻辑：Excel有值优先 → 部分缺失补全 → 无值则按上报单位推导
            provinceAutoFillTool.fillAssetProvinceCity(entity, false);
            log.debug("数据内容资产导入自动填充省市：ID={}, 单位={}, 省={}, 市={}",
                    entity.getId(), entity.getReportUnit(), entity.getProvince(), entity.getCity());

            entities.add(entity);
        }

        baseMapper.insertBatch(entities);
        log.info("数据资产批量入库成功，共{}条", entities.size());

        // ============ 新增：上报单位表同步（批量导入场景） ============
        // 遍历所有成功保存的实体，同步上报单位表状态
        for (DataContentAsset entity : entities) {
            provinceAutoFillTool.syncReportUnit(
                    entity.getReportUnit(),  // 上报单位名称
                    entity.getProvince(),    // 填充后的省份
                    "dataContent",           // 资产类型：数据内容
                    false                    // isDelete=false：新增场景
            );
        }
        log.info("数据内容资产批量导入完成，已同步上报单位表状态");
    }

    @Override
    public void handleImportResult(int totalRow, int validRow, List<ExcelErrorVO> errorList) {
        log.info("==== 数据资产Excel导入结果 ====");
        log.info("总记录数：{} | 成功入库：{}条 | 错误数：{}条", totalRow, validRow, errorList.size());
        if (!errorList.isEmpty()) {
            errorList.forEach(error ->
                    log.warn("行{}：{}（{}）", error.getExcelRowNum(), error.getErrorFields(), error.getErrorMsg())
            );
        }
    }

    @Override
    public DataContentAsset getById(String id) {
        // 移除32位长度限制，只检查非空和格式
        if (!StringUtils.hasText(id) || !isValidAssetId(id)) {
            throw new RuntimeException("数据资产ID格式错误，必须由字母和数字组成");
        }

        DataContentAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            throw new RuntimeException("ID为" + id + "的数据资产不存在");
        }
        log.info("查询数据资产详情成功，ID：{}", id);
        return asset;
    }

    // ====================== 数据内容资产联合查询方法实现 ======================
    @Override
    public Object combinedQuery(Integer pageNum, Integer pageSize,
                                String reportUnit, String province, String city,
                                String applicationField, String developmentTool,  Integer quantityMin, Integer quantityMax,
                                String updateCycle, String updateMethod, String inventoryUnit) {
        try {
            log.info("执行数据内容资产联合查询：pageNum={}, pageSize={}, reportUnit={}, province={}, city={}, " +
                            "applicationField={}, developmentTool={}, quantityMin={}, quantityMax={}, updateCycle={}, updateMethod={}, inventoryUnit={}",
                    pageNum, pageSize, reportUnit, province, city, applicationField,
                    developmentTool, quantityMin, quantityMax, updateCycle, updateMethod, inventoryUnit);

            // 创建分页对象，使用MyBatis-Plus的分页功能
            Page<DataContentAsset> page = new Page<>(pageNum, pageSize);

            // 调用Mapper进行联合查询
            Page<DataContentAsset> resultPage = baseMapper.combinedQuery(
                    page, reportUnit, province, city, applicationField,
                    developmentTool, quantityMin, quantityMax, updateCycle, updateMethod, inventoryUnit
            );

            log.info("数据内容资产联合查询完成，共查询到{}条记录，分{}页显示",
                    resultPage.getTotal(), resultPage.getPages());
            return resultPage;

        } catch (Exception e) {
            log.error("数据内容资产联合查询执行失败", e);
            throw new RuntimeException("联合查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 新增数据内容资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于新增单条数据内容资产记录，包含完整的数据校验、业务处理、数据保存和上报单位表同步功能。
     * 数据内容资产表与其他资产表的主要区别：有省市字段，需要同时维护自身省市字段和上报单位表。
     * 核心流程：
     * 1. 自动填充省市阶段 → 2. 数据校验阶段 → 3. 数据处理阶段 → 4. 数据保存阶段 → 5. 上报单位表同步阶段

     *  数据校验规则（按字段顺序）：
     * 1.1 主键：必填，数字字母组合，确保唯一性
     * 1.2 上报单位：必填字段
     * 1.3 分类编码与资产分类：必填，严格匹配预设映射关系
     * 1.4 资产名称：必填字段
     * 1.5 应用领域：必填，固定选项
     * 1.6 开发工具：必填，固定选项
     * 1.7 实有数量：必填，非负整数（支持0）
     * 1.8 计量单位：必填字段
     * 1.9 单价：可选字段，如果填写则必须为非负数
     * 1.10 更新周期：可选字段，但如果有值必须是固定选项
     * 1.11 更新方式：可选字段，但如果有值必须是固定选项
     * 1.12 盘点单位：必填字段

     * 特殊处理逻辑：
     * - 省市自动填充：根据上报单位名称自动推导省市信息
     * - 金额字段：如果金额为空，且单价和实有数量都存在，则自动计算金额（单价×数量）
     * - 创建时间：系统自动生成当前时间
     * - 上报单位同步：使用填充后的省市信息同步到上报单位表

     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或保存失败都会回滚整个事务

     * 适用场景：
     * - 前端手动新增数据内容资产
     * - 需要完整校验和上报单位同步的业务场景
     * - 单条记录新增操作

     * 注意事项：
     * - 数据内容资产表只有一个分类："数据内容资产"
     * - 开发工具必须从固定选项中选择
     * - 应用领域、更新周期、更新方式 必须有值，从固定选项中选择
     * - 金额计算尊重用户输入，仅在金额为空时自动计算
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(DataContentAsset asset) {
        log.info("开始新增数据内容资产，ID：{}", asset.getId());

        // ==================== 1. 自动填充省市阶段 ====================

        // 1.1 自动填充省市信息（新增模式，尊重Excel原有值）
        // 如果资产中已经填写了省市信息，则保留；如果为空，则根据单位名称自动推导
        provinceAutoFillTool.fillAssetProvinceCity(asset, false);
        log.debug("省市自动填充完成 - 省份：{}，城市：{}", asset.getProvince(), asset.getCity());

        // ==================== 2. 数据校验阶段 ====================

        // 2.1 主键校验：必填，数字字母组合，确保唯一
        validatePrimaryKey(asset);

        // 2.2 上报单位校验：必填
        validateReportUnit(asset);

        // 2.3 分类编码与资产分类校验：必填，严格匹配
        validateCategory(asset);

        // 2.4 资产名称校验：必填
        validateAssetName(asset);

        // 2.5 应用领域校验：必填，但如果有值必须是固定选项
        validateApplicationField(asset);

        // 2.6 开发工具校验：必填，固定选项
        validateDevelopmentTool(asset);

        // 2.7 实有数量校验：必填，非负整数
        validateActualQuantity(asset);

        // 2.8 计量单位校验：必填
        validateUnit(asset);

        // 2.9 单价校验：可选，如果填写则必须非负
        validateUnitPrice(asset);

        // 2.10 更新周期校验：可选，但如果有值必须是固定选项
        validateUpdateCycle(asset);

        // 2.11 更新方式校验：可选，但如果有值必须是固定选项
        validateUpdateMethod(asset);

        // 2.12 盘点单位校验：必填
        validateInventoryUnit(asset);

        // ==================== 3. 数据处理阶段 ====================

        // 3.1 系统自动生成创建时间
        asset.setCreateTime(LocalDateTime.now());

        // 3.2 计算金额（如果金额为空，且有单价和数量，则自动计算）
        calculateAmount(asset);

        // ==================== 4. 数据保存阶段 ====================

        baseMapper.insert(asset);
        log.info("新增数据内容资产成功，ID：{}，资产名称：{}", asset.getId(), asset.getAssetName());

        // ==================== 5. 上报单位表同步阶段 ====================

        // 5.1 上报单位表同步（单条新增场景）
        // 使用填充后的省市信息同步上报单位表，设置数据资产状态标志为1
        provinceAutoFillTool.syncReportUnit(
                asset.getReportUnit(),  // 上报单位名称
                asset.getProvince(),    // 数据资产有省份字段，使用填充后的省份
                "dataContent",          // 资产类型：数据内容
                false                   // isDelete=false：新增场景
        );
        log.debug("数据内容资产新增完成，已同步上报单位表状态");
    }

// ==================== 详细的校验方法 ====================

    /**
     * 2.1 主键校验
     * 规则：必填，唯一标识，数字字母组合，确保在组内唯一且不与之前组别冲突
     */
    private void validatePrimaryKey(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getId())) {
            throw new RuntimeException("主键不能为空");
        }

        // 数字字母组合校验
        if (!isValidAssetId(asset.getId())) {
            throw new RuntimeException("主键格式错误，必须由字母和数字组成");
        }

        // 唯一性校验
        if (getExistingIds().contains(asset.getId())) {
            throw new RuntimeException("主键已存在：" + asset.getId() + "，请更换ID");
        }
    }

    /**
     * 2.2 上报单位校验
     * 规则：必填
     */
    private void validateReportUnit(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getReportUnit())) {
            throw new RuntimeException("上报单位不能为空");
        }
        log.debug("上报单位：{}", asset.getReportUnit());
    }

    /**
     * 2.3 分类编码与资产分类校验
     * 规则：必填，与资产分类严格匹配，使用CategoryMapUtils中的数据表映射
     */
    private void validateCategory(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getCategoryCode())) {
            throw new RuntimeException("分类编码不能为空");
        }

        if (!StringUtils.hasText(asset.getAssetCategory())) {
            throw new RuntimeException("资产分类不能为空");
        }

        // 分类匹配校验
        if (!checkCategoryMatch(asset.getCategoryCode(), asset.getAssetCategory())) {
            throw new RuntimeException("分类不匹配！编码" + asset.getCategoryCode() +
                    "对应的正确分类应为：" + CATEGORY_MAP.get(asset.getCategoryCode()));
        }

        log.debug("分类编码：{}，资产分类：{}", asset.getCategoryCode(), asset.getAssetCategory());
    }

    /**
     * 2.4 资产名称校验
     * 规则：必填
     */
    private void validateAssetName(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getAssetName())) {
            throw new RuntimeException("资产名称不能为空");
        }
        log.debug("资产名称：{}", asset.getAssetName());
    }

    /**
     * 2.5 应用领域校验
     * 规则：必填，固定选项：后勤保障、建设规划、其他、日常办公、战备管理、政治工作、装备保障、作战指挥
     */
    private void validateApplicationField(DataContentAsset asset) {
//        // 可选字段，但如果有值则必须是固定选项
//        if (StringUtils.hasText(asset.getApplicationField())) {
//            List<String> applicationFields = Arrays.asList(
//                    "后勤保障", "建设规划", "其他", "日常办公",
//                    "战备管理", "政治工作", "装备保障", "作战指挥"
//            );
//
//            if (!applicationFields.contains(asset.getApplicationField())) {
//                throw new RuntimeException("无效的应用领域：" + asset.getApplicationField() +
//                        "，允许值：" + String.join("、", applicationFields));
//            }
//            log.debug("应用领域：{}", asset.getApplicationField());
//        }

        if (!StringUtils.hasText(asset.getApplicationField())) {
            throw new RuntimeException("应用领域不能为空");
        }
        List<String> applicationFields = Arrays.asList(
                "后勤保障", "建设规划", "其他", "日常办公",
                "战备管理", "政治工作", "装备保障", "作战指挥"
        );
        if (!applicationFields.contains(asset.getApplicationField())) {
            throw new RuntimeException("无效的应用领域：" + asset.getApplicationField() +
                    "，允许值：" + String.join("、", applicationFields));
        }
        log.debug("应用领域：{}", asset.getApplicationField());

    }

    /**
     * 2.6 开发工具校验
     * 规则：必填，固定选项：Oracle、HDFS、MySql、SQL Server、达梦、高斯、南大通用、其他、人大金仓、神州通用
     */
    private void validateDevelopmentTool(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getDevelopmentTool())) {
            throw new RuntimeException("开发工具不能为空");
        }

        List<String> developmentTools = Arrays.asList(
                "Oracle", "HDFS", "MySql", "SQL Server", "达梦", "高斯",
                "南大通用", "其他", "人大金仓", "神州通用"
        );

        if (!developmentTools.contains(asset.getDevelopmentTool())) {
            throw new RuntimeException("无效的开发工具：" + asset.getDevelopmentTool() +
                    "，允许值：" + String.join("、", developmentTools));
        }

        log.debug("开发工具：{}", asset.getDevelopmentTool());
    }

    /**
     * 2.7 实有数量校验
     * 规则：必填，非负整数
     */
    private void validateActualQuantity(DataContentAsset asset) {
        if (asset.getActualQuantity() == null) {
            throw new RuntimeException("实有数量不能为空");
        }

        if (asset.getActualQuantity() < 0) {
            throw new RuntimeException("实有数量必须为非负整数");
        }

        log.debug("实有数量：{}", asset.getActualQuantity());
    }

    /**
     * 2.8 计量单位校验
     * 规则：必填，如"GB"、"MB"等，无固定选项
     */
    private void validateUnit(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getUnit())) {
            throw new RuntimeException("计量单位不能为空");
        }
        log.debug("计量单位：{}", asset.getUnit());
    }

    /**
     * 2.9 单价校验
     * 规则：可选，如果填写则必须非负
     */
    private void validateUnitPrice(DataContentAsset asset) {
        // 可选字段，如果有值则校验非负
        if (asset.getUnitPrice() != null) {
            if (asset.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("单价不能为负数");
            }
            log.debug("单价：{}", asset.getUnitPrice());
        }
    }

    /**
     * 2.10 更新周期校验
     * 规则：必填，固定选项：每月、每年、不更新、每半年、每季度、每天、其他、实时
     */
//    private void validateUpdateCycle(DataContentAsset asset) {
//        // 可选字段，但如果有值则必须是固定选项
//        if (StringUtils.hasText(asset.getUpdateCycle())) {
//            List<String> updateCycles = Arrays.asList(
//                    "每月", "每年", "不更新", "每半年", "每季度", "每天", "其他", "实时"
//            );
//
//            if (!updateCycles.contains(asset.getUpdateCycle())) {
//                throw new RuntimeException("无效的更新周期：" + asset.getUpdateCycle() +
//                        "，允许值：" + String.join("、", updateCycles));
//            }
//            log.debug("更新周期：{}", asset.getUpdateCycle());
//        }
//    }
    private void validateUpdateCycle(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getUpdateCycle())) {
            throw new RuntimeException("更新周期不能为空");
        }

        List<String> updateCycles = Arrays.asList(
                "每月", "每年", "不更新", "每半年", "每季度", "每天", "其他", "实时"
        );

        if (!updateCycles.contains(asset.getUpdateCycle())) {
            throw new RuntimeException("无效的更新周期：" + asset.getUpdateCycle() +
                        "，允许值：" + String.join("、", updateCycles));
        }
        log.debug("更新周期：{}", asset.getUpdateCycle());
    }


    /**
     * 2.11 更新方式校验
     * 规则：必填，固定选项：在线填报、离线填报、其他、商业购置、上级请领、自动采集
     */
//    private void validateUpdateMethod(DataContentAsset asset) {
//        // 可选字段，但如果有值则必须是固定选项
//        if (StringUtils.hasText(asset.getUpdateMethod())) {
//            List<String> updateMethods = Arrays.asList(
//                    "在线填报", "离线填报", "其他", "商业购置", "上级请领", "自动采集"
//            );
//
//            if (!updateMethods.contains(asset.getUpdateMethod())) {
//                throw new RuntimeException("无效的更新方式：" + asset.getUpdateMethod() +
//                        "，允许值：" + String.join("、", updateMethods));
//            }
//            log.debug("更新方式：{}", asset.getUpdateMethod());
//        }
//    }
    private void validateUpdateMethod(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getUpdateMethod())) {
            throw new RuntimeException("更新方式不能为空");
        }

        List<String> updateMethods = Arrays.asList(
                    "在线填报", "离线填报", "其他", "商业购置", "上级请领", "自动采集"
        );

        if (!updateMethods.contains(asset.getUpdateMethod())) {
                throw new RuntimeException("无效的更新方式：" + asset.getUpdateMethod() +
                        "，允许值：" + String.join("、", updateMethods));
        }
        log.debug("更新方式：{}", asset.getUpdateMethod());

    }


    /**
     * 2.12 盘点单位校验
     * 规则：必填，负责盘点的单位
     */
    private void validateInventoryUnit(DataContentAsset asset) {
        if (!StringUtils.hasText(asset.getInventoryUnit())) {
            throw new RuntimeException("盘点单位不能为空");
        }
        log.debug("盘点单位：{}", asset.getInventoryUnit());
    }

// ==================== 金额计算方法 ====================

    /**
     * 金额计算
     * 规则：可选，数量×单价
     * 逻辑：如果金额为空，且有单价和实有数量，则自动计算
     * 如果金额已有值，则不自动计算（尊重用户输入）
     */
    private void calculateAmount(DataContentAsset asset) {
        // 只有当金额为空，且单价和实有数量都存在时，才自动计算
        if (asset.getAmount() == null && asset.getUnitPrice() != null && asset.getActualQuantity() != null) {
            BigDecimal amount = asset.getUnitPrice().multiply(BigDecimal.valueOf(asset.getActualQuantity()));
            asset.setAmount(amount);
            log.debug("自动计算金额：单价 {} × 数量 {} = 金额 {}",
                    asset.getUnitPrice(), asset.getActualQuantity(), amount);
        } else if (asset.getAmount() != null) {
            log.debug("使用用户输入的金额：{}", asset.getAmount());
        } else {
            log.debug("金额字段为空，且缺少计算条件（单价或数量）");
        }
    }

    /**
//     * 修改数据内容资产（集成上报单位表同步 + 更新创建时间）
//     * 功能概述：
//     * 本方法用于修改单条数据内容资产记录，包含数据校验、业务处理、数据更新和上报单位表同步功能。
//     * 核心特点：修改成功后，将创建时间更新为当前时间，作为最后修改时间的参考。
//
//     * 数据校验规则（与新增一致）：
//     * 3.1 主键校验：必填，确保存在
//     * 3.2 上报单位校验：必填
//     * 3.3 分类编码与资产分类校验：必填，严格匹配
//     * 3.4 资产名称校验：必填
//     * 3.5 应用领域校验：必填，固定选项
//     * 3.6 开发工具校验：必填，固定选项
//     * 3.7 实有数量校验：必填，非负整数
//     * 3.8 计量单位校验：必填
//     * 3.9 单价校验：可选，如果填写则必须非负
//     * 3.10 更新周期校验：必填，固定选项
//     * 3.11 更新方式校验：必填，固定选项


     // ==================== 1121 核心业务方法 ====================
     /**
     * 🔄 修改数据内容资产 - 完整的业务逻辑实现

     * ==================== 方法概述 ====================
     * 本方法处理数据内容资产的修改操作，是系统中重要的业务方法之一。
     * 包含完整的业务逻辑链：数据校验 → 智能处理 → 数据更新 → 状态同步 → 跨表同步

     * ==================== 核心特性 ====================
     * ✅ 支持6种不同的修改场景处理
     * ✅ 智能的省市推导和标准化处理
     * ✅ 精确的上报单位表状态同步
     * ✅ 条件性的跨表数据同步（数据表 → 网信表）
     * ✅ 完整的事务管理和异常处理

     * ==================== 与网信资产的区别 ====================
     * 1. 校验规则不同：数据资产有特有的应用领域、开发工具等字段校验
     * 2. 跨表同步方向：数据表 → 网信表（与网信表相反）
     * 3. 业务字段不同：数据资产特有的更新周期、更新方式等字段

     * ==================== 事务管理 ====================
     * 使用@Transactional注解确保所有数据库操作的原子性
     * 任何步骤失败都会回滚整个事务，保证数据一致性
     *
     * @param asset 数据内容资产对象（包含用户修改后的数据）
     * @throws RuntimeException 当资产不存在、数据校验失败或更新失败时抛出业务异常
     *
     * @apiNote 本方法遵循与网信资产相同的设计模式，确保系统行为的一致性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DataContentAsset asset) {
        log.info("🔄 [数据资产] 开始修改数据内容资产，ID：{}", asset.getId());

        // ==================== 阶段1：数据存在性校验 ====================
        log.debug("📋 [阶段1] 开始数据存在性校验");

        // 1.1 主键ID非空校验：确保修改操作有明确的目标记录
        if (!StringUtils.hasText(asset.getId())) {
            throw new RuntimeException("修改数据内容资产失败：主键ID不能为空");
        }

        // 1.2 原记录查询：获取数据库中现有的资产记录，用于变更比较和数据回滚
        DataContentAsset existingAsset = baseMapper.selectById(asset.getId());
        if (existingAsset == null) {
            throw new RuntimeException("修改数据内容资产失败：资产不存在，ID：" + asset.getId());
        }

        // 1.3 变更信息记录：保存原始数据，用于后续的变更检测和同步决策
        String originalReportUnit = existingAsset.getReportUnit();
        String newReportUnit = asset.getReportUnit();
        String originalProvince = existingAsset.getProvince();
        String originalCity = existingAsset.getCity();
        String newProvince = asset.getProvince();
        String newCity = asset.getCity();

        // 1.4 变更状态分析：精确识别用户修改了哪些字段
        boolean reportUnitChanged = !Objects.equals(originalReportUnit, newReportUnit);
        boolean provinceChanged = !Objects.equals(originalProvince, newProvince);
        boolean cityChanged = !Objects.equals(originalCity, newCity);
        boolean userModifiedProvinceCity = provinceChanged || cityChanged;
        boolean compositeModification = reportUnitChanged && userModifiedProvinceCity;

        log.debug("📋 [阶段1] 数据存在性校验完成 - 单位变更: {}, 省变更: {}, 市变更: {}, 用户修改省市: {}, 复合修改: {}",
                reportUnitChanged, provinceChanged, cityChanged, userModifiedProvinceCity, compositeModification);

        // ==================== 阶段2：智能省市处理 ====================
        log.debug("🌍 [阶段2] 开始智能省市处理");

        if (compositeModification) {
            // 🎯 场景6：用户同时修改了单位和省市（复合场景）
            log.info("🎯 检测到复合修改场景：同时修改单位和省市，用户输入绝对优先");
            handleCompositeModification(asset, existingAsset);
        } else if (userModifiedProvinceCity) {
            // 🎯 场景1-3：用户手动修改了省市信息
            log.debug("🎯 用户手动修改了省市信息，进行智能补全和标准化");
            handleUserModifiedProvinceCity(asset, existingAsset);
        } else if (reportUnitChanged) {
            // 🎯 场景4：用户只修改了上报单位
            log.debug("🎯 用户修改了上报单位，重新推导省市");
            handleUnitChangedProvinceCity(asset, newReportUnit);
        } else {
            // 🎯 场景5：用户未修改任何信息，保持原样
            log.debug("🎯 用户未修改省市和单位，保持原有省市");
            // 不需要处理，直接使用原有省市
        }

        log.debug("🌍 [阶段2] 智能省市处理完成 - 最终省市: {}-{}", asset.getProvince(), asset.getCity());

//        // ==================== 阶段3：省市字段严格校验 ====================
//        log.debug("🔍 [阶段3] 开始省市字段校验");
//        validateProvinceCity(asset.getProvince(), asset.getCity());

        // ==================== 阶段4：其他业务数据校验 ====================
        log.debug("✅ [阶段4] 开始业务数据校验");
        validateBusinessFields(asset);

        log.debug("✅ [阶段4] 业务数据校验通过，ID：{}", asset.getId());

        // ==================== 阶段5：数据处理 ====================
        log.debug("💰 [阶段5] 开始数据处理");
        calculateAmount(asset);

        // ==================== 阶段6：数据更新 ====================
        log.debug("💾 [阶段6] 开始数据更新");

        // 6.1 更新创建时间为当前时间（作为最后修改时间的参考）
        asset.setCreateTime(LocalDateTime.now());

        // 6.2 执行数据库更新操作
        int updateCount = baseMapper.updateById(asset);
        if (updateCount == 0) {
            throw new RuntimeException("修改数据内容资产失败，ID：" + asset.getId());
        }

        log.info("✅ [阶段6] 修改数据内容资产成功，ID：{}，资产名称：{}", asset.getId(), asset.getAssetName());

        // ==================== 阶段7：上报单位表同步 ====================
        log.debug("🔄 [阶段7] 开始上报单位表同步");

        /**
         * 📍 上报单位表同步触发条件：
         * 1. 修改了上报单位 → 必须同步（更新原单位状态 + 新增/更新新单位）
         * 2. 修改了省市 → 必须同步（更新单位对应的省市信息）

         * 注意：只要满足以上任一条件就要进行同步
         */
        boolean needUnitSync = reportUnitChanged || userModifiedProvinceCity;

        if (needUnitSync) {
            log.debug("🔄 触发上报单位表同步 - 单位变更: {}, 省市变更: {}", reportUnitChanged, userModifiedProvinceCity);
            syncReportUnitWithChange(originalReportUnit, newReportUnit,
                    existingAsset.getProvince(), asset.getProvince(),
                    reportUnitChanged, userModifiedProvinceCity);
        } else {
            log.debug("⏭️ 未触发上报单位表同步 - 单位和省市均未修改");
        }

        // ==================== 阶段8：跨表同步决策与执行 ====================
        log.debug("🔄 [阶段8] 开始跨表同步决策");

        /**
         * 📍 跨表同步触发条件（更严格）：
         * 1. 单位在上报单位表中存在
         * 2. 省市发生了改变

         * 两个条件必须同时满足才进行跨表同步

         * 🎯 同步方向：数据表 → 网信表
         */
        boolean needCrossSync = needCrossTableSync(newReportUnit, originalProvince, originalCity,
                asset.getProvince(), asset.getCity());

        if (needCrossSync) {
            log.info("🔄 满足跨表同步条件，开始跨表同步");
            syncToCyberTable(newReportUnit, asset.getProvince(), asset.getCity());
            log.info("✅ 跨表同步完成");
        } else {
            log.debug("⏭️ 不满足跨表同步条件，跳过同步");
        }

        log.info("🎉 [数据资产] 修改操作全部完成，ID：{}", asset.getId());
    }

    // ==================== 省市处理核心方法 ====================

    /**
     * 🎯 处理复合修改场景：用户同时修改上报单位和省市

     * ==================== 方法说明 ====================
     * 这是最复杂的修改场景，用户同时改变了单位和省市信息。
     * 核心原则：用户输入的省市信息具有绝对优先权，不进行任何自动推导。

     * ==================== 处理逻辑 ====================
     * 1. 直接使用用户输入的省市信息，不进行任何推导
     * 2. 只进行标准化处理，确保数据格式统一
     * 3. 记录详细的变更日志，便于审计和问题追踪
     *
     * @param asset 当前资产对象（包含用户修改后的数据）
     * @param existingAsset 原始资产对象（用于获取原始信息和变更比较）
     *
     * @apiNote 此场景下完全信任用户输入，系统只负责格式标准化
     *          适用于用户明确知道新单位对应省市的情况
     */
    private void handleCompositeModification(DataContentAsset asset, DataContentAsset existingAsset) {
        String userProvince = asset.getProvince();
        String userCity = asset.getCity();
        String originalProvince = existingAsset.getProvince();
        String originalCity = existingAsset.getCity();

        log.debug("🤖 复合修改场景处理 - 用户输入省市: {}-{}, 原始省市: {}-{}",
                userProvince, userCity, originalProvince, originalCity);

        // 🎯 原则：用户输入的省市信息具有最高优先级
        // 直接使用用户输入的省市，只进行标准化处理，不进行任何推导

        // 🆕 新增：使用统一的标准化处理，确保省市格式一致
        standardizeProvinceCity(asset);

        log.debug("✅ 复合修改处理完成 - 最终省市: {}-{}", asset.getProvince(), asset.getCity());

        // 记录详细的变更信息，用于审计追踪
        log.info("📝 复合修改记录 - 单位: {} → {}, 省市: {}-{} → {}-{}",
                existingAsset.getReportUnit(), asset.getReportUnit(),
                originalProvince, originalCity, asset.getProvince(), asset.getCity());
    }

    /**
     * 🎯 处理用户手动修改省市的情况（优化版）

     * ==================== 方法说明 ====================
     * 处理用户单独修改省市信息的场景，根据用户修改的具体情况进行智能补全。
     * 确保即使用户只修改部分省市信息，也能得到完整准确的省市数据。

     * ==================== 场景覆盖 ====================
     * 场景1：用户同时修改了省和市 → 直接标准化处理
     * 场景2：用户只修改了省 → 补全市信息（省份首府）
     * 场景3：用户只修改了市 → 补全省信息（根据城市推导省份）
     *
     * @param asset 当前资产对象（包含用户修改后的数据）
     * @param existingAsset 原始资产对象（用于比较哪些字段被修改）
     *
     * @apiNote 此方法确保省市信息的完整性，避免出现有省无市或有市无省的情况
     */
    private void handleUserModifiedProvinceCity(DataContentAsset asset, DataContentAsset existingAsset) {
        String userProvince = asset.getProvince();
        String userCity = asset.getCity();
        String originalProvince = existingAsset.getProvince();
        String originalCity = existingAsset.getCity();

        boolean provinceChanged = !Objects.equals(originalProvince, userProvince);
        boolean cityChanged = !Objects.equals(originalCity, userCity);

        log.debug("🤖 用户修改省市分析 - 省变更: {}, 市变更: {}, 用户输入: {}-{}",
                provinceChanged, cityChanged, userProvince, userCity);

        if (provinceChanged && cityChanged) {
            // 🎯 场景1：用户同时修改了省和市
            log.debug("🎯 用户同时修改了省和市，进行标准化处理");
            standardizeProvinceCity(asset);

        } else if (provinceChanged && !cityChanged) {
            // 🎯 场景2：用户只修改了省，未修改市
            log.debug("🎯 用户只修改了省，补全市信息（省份首府）");
            // 🆕 优化：先标准化省份名称
            String standardizedProvince = standardizeProvinceName(userProvince);
            asset.setProvince(standardizedProvince);

            try {
                String capital = areaCacheTool.getCapitalByProvinceName(standardizedProvince);
                if (StringUtils.hasText(capital)) {
                    asset.setCity(capital);
                    log.debug("✅ 成功补全首府 - 省: {}, 市: {}", standardizedProvince, capital);
                } else {
                    log.warn("⚠️ 无法找到省份的首府，使用原城市信息");
                    asset.setCity(originalCity);
                }
            } catch (Exception e) {
                log.error("❌ 获取首府时出错，使用原城市信息", e);
                asset.setCity(originalCity);
            }

        } else if (!provinceChanged && cityChanged) {
            // 🎯 场景3：用户只修改了市，未修改省
            log.debug("🎯 用户只修改了市，补全省信息");
            // 🆕 优化：先标准化城市名称
            String standardizedCity = standardizeCityName(userCity);
            asset.setCity(standardizedCity);

            try {
                // 🆕 优化：使用增强的城市到省份映射，支持简写匹配 （关键！）
                String province = findProvinceByCity(standardizedCity);
                if (StringUtils.hasText(province)) {
                    asset.setProvince(province);
                    log.debug("✅ 成功推导省份 - 市: {}, 省: {}", standardizedCity, province);
                } else {
                    log.warn("⚠️ 无法根据城市推导省份，请检查修改的市，便于恢复原省份信息");
                    asset.setProvince(originalProvince);
                }
            } catch (Exception e) {
                log.error("❌ 获取省份时出错，请检查修改的市，使用原省份信息", e);
                asset.setProvince(originalProvince);
            }
        }
    }

    /**
     * 🎯 处理单位变更时的省市推导（优化版）

     * ==================== 方法说明 ====================
     * 当用户只修改上报单位时，智能推导新单位对应的省市信息。
     * 采用两级优化策略：优先使用上报单位表中的已有信息，避免重复推导。

     * ==================== 优化策略 ====================
     * 策略1：查询上报单位表，如果单位存在且省份有效 → 直接使用该省份，补全首府
     * 策略2：如果单位不存在或省份无效 → 使用工具类智能推导
     *
     * @param asset 当前资产对象
     * @param newReportUnit 新的上报单位名称
     *
     * @apiNote 这种优化策略显著提升处理效率，特别在单位信息相对稳定的场景下
     */
    private void handleUnitChangedProvinceCity(DataContentAsset asset, String newReportUnit) {
        log.debug("🤖 单位变更，开始推导省市 - 新单位: {}", newReportUnit);

        // 🎯 策略1：优先从上报单位表中获取省份信息
        ReportUnit reportUnit = reportUnitMapper.selectByReportUnitName(newReportUnit);
        if (reportUnit != null && StringUtils.hasText(reportUnit.getProvince()) &&
                !"未知".equals(reportUnit.getProvince())) {

            // 🎯 单位表中存在有效省份，直接使用并补全首府
            String provinceFromTable = reportUnit.getProvince();
            asset.setProvince(provinceFromTable);

            try {
                String capital = areaCacheTool.getCapitalByProvinceName(provinceFromTable);
                if (StringUtils.hasText(capital)) {
                    asset.setCity(capital);
                    log.info("✅ 从上报单位表获取省市成功 - 单位: {}, 省: {}, 市: {}",
                            newReportUnit, provinceFromTable, capital);
                } else {
                    log.warn("⚠️ 无法找到省份的首府，使用工具类推导城市");
                    useToolToDeriveCity(asset, provinceFromTable);
                }
            } catch (Exception e) {
                log.error("❌ 获取首府时出错，使用工具类推导", e);
                useToolToDeriveCity(asset, provinceFromTable);
            }
        } else {
            // 🎯 策略2：单位表中不存在，使用工具类完整推导
            log.debug("🔍 上报单位表中无记录，使用工具类推导");
            useToolToDeriveProvinceCity(asset, newReportUnit);
        }
    }

    // ==================== 🆕 新增：增强的省市匹配方法 ====================

    /**
     * 🆕 根据城市名称查找对应的省份（增强版）

     * ==================== 方法说明 ====================
     * 使用标准化的城市名称和简写匹配逻辑，提高城市到省份映射的准确性。
     * 支持多种行政区划类型：地级市、县级市、自治州、地区、盟、特别行政区等。

     * ==================== 匹配策略 ====================
     * 1. 精确匹配：直接在城市到省份映射表中查找
     * 2. 简写匹配：使用城市简写进行匹配
     * 3. 标准化匹配：对输入城市名称进行标准化后再匹配

     * @param cityName 城市名称（支持全称或简写）
     * @return 对应的省份名称，如未找到返回null
     */
    private String findProvinceByCity(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            return null;
        }

        Map<String, String> cityToProvinceMap = areaCacheTool.getCityToProvinceMap();

        // 1. 精确匹配：直接查找
        if (cityToProvinceMap.containsKey(cityName)) {
            return cityToProvinceMap.get(cityName);
        }

        // 2. 简写匹配：遍历所有城市，使用简写进行匹配
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            String cityAbbr = getCityAbbreviation(standardCity);
            if (cityName.equals(cityAbbr)) {
                log.debug("🔍 城市简写匹配成功: '{}' → '{}' → '{}'",
                        cityName, cityAbbr, standardCity);
                return cityToProvinceMap.get(standardCity);
            }
        }

        // 3. 标准化匹配：对输入进行标准化后再尝试
        String standardizedCity = standardizeCityName(cityName);
        if (!cityName.equals(standardizedCity) && cityToProvinceMap.containsKey(standardizedCity)) {
            log.debug("🔍 城市标准化匹配成功: '{}' → '{}'", cityName, standardizedCity);
            return cityToProvinceMap.get(standardizedCity);
        }

        log.debug("❌ 未找到城市对应的省份: {}", cityName);
        return null;
    }

    // ==================== 工具类调用方法 ====================

    /**
     * 🛠️ 使用工具类推导城市（已知省份）
     *
     * @param asset 当前资产对象
     * @param province 已知的省份名称
     */
    private void useToolToDeriveCity(DataContentAsset asset, String province) {
        HasReportUnitAndProvince tempAsset = new HasReportUnitAndProvince() {
            @Override
            public String getReportUnit() { return asset.getReportUnit(); }
            @Override
            public String getProvince() { return province; }
            @Override
            public void setProvince(String p) { /* 不修改省份 */ }
            @Override
            public String getCity() { return asset.getCity(); }
            @Override
            public void setCity(String city) { asset.setCity(city); }
        };

        provinceAutoFillTool.fillAssetProvinceCity(tempAsset, false);
        log.debug("🤖 工具类推导城市完成 - 省: {}, 市: {}", province, asset.getCity());
    }

    /**
     * 🛠️ 使用工具类完整推导省市
     *
     * @param asset 当前资产对象
     * @param reportUnit 上报单位名称
     */
    private void useToolToDeriveProvinceCity(DataContentAsset asset, String reportUnit) {
        HasReportUnitAndProvince tempAsset = new HasReportUnitAndProvince() {
            @Override
            public String getReportUnit() { return reportUnit; }
            @Override
            public String getProvince() { return asset.getProvince(); }
            @Override
            public void setProvince(String province) { asset.setProvince(province); }
            @Override
            public String getCity() { return asset.getCity(); }
            @Override
            public void setCity(String city) { asset.setCity(city); }
        };

        provinceAutoFillTool.fillAssetProvinceCity(tempAsset, false);
        log.debug("🤖 工具类完整推导完成 - 单位: {}, 省市: {}-{}",
                reportUnit, asset.getProvince(), asset.getCity());
    }

    // ==================== 同步相关方法 ====================

    /**
     * 🔄 精确的上报单位表同步方法

     * ==================== 同步策略 ====================
     * 情况1：只修改单位
     *   - 原单位：标记删除检查
     *   - 新单位：新增或更新

     * 情况2：只修改省市
     *   - 当前单位：更新省市信息

     * 情况3：同时修改单位和省市
     *   - 原单位：标记删除检查
     *   - 新单位：使用新的省市信息新增或更新
     *
     * @param originalUnit 原始单位名称
     * @param newUnit 新单位名称
     * @param originalProvince 原始省份
     * @param newProvince 新省份
     * @param unitChanged 单位是否变更
     * @param provinceChanged 省市是否变更
     */
    private void syncReportUnitWithChange(String originalUnit, String newUnit,
                                          String originalProvince, String newProvince,
                                          boolean unitChanged, boolean provinceChanged) {
        log.debug("🔄 开始精确上报单位表同步 - 单位变更: {}, 省市变更: {}", unitChanged, provinceChanged);

        if (unitChanged) {
            if (StringUtils.hasText(originalUnit)) {
                provinceAutoFillTool.syncReportUnit(originalUnit, originalProvince, "dataContent", true);
                log.debug("✅ 原单位标记删除检查完成: {}", originalUnit);
            }

            if (StringUtils.hasText(newUnit)) {
                provinceAutoFillTool.syncReportUnit(newUnit, newProvince, "dataContent", false);
                log.debug("✅ 新单位同步完成: {}", newUnit);
            }

        } else if (provinceChanged) {
            if (StringUtils.hasText(newUnit)) {
                provinceAutoFillTool.syncReportUnit(newUnit, newProvince, "dataContent", false);
                log.debug("✅ 单位省市更新完成: {} -> {}", newUnit, newProvince);
            }
        }

        log.info("✅ 上报单位表同步完成");
    }

    /**
     * 🔍 跨表同步条件判断（精确版）

     * ==================== 触发条件 ====================
     * 条件1：省市必须发生改变（省或市任一改变）
     * 条件2：单位必须在上报单位表中存在

     * 两个条件必须同时满足才进行跨表同步
     *
     * @param newUnit 新单位名称
     * @param oldProvince 原始省份
     * @param oldCity 原始城市
     * @param newProvince 新省份
     * @param newCity 新城市
     * @return 是否需要跨表同步
     */
    private boolean needCrossTableSync(String newUnit, String oldProvince, String oldCity,
                                       String newProvince, String newCity) {
        // 条件1：省市必须发生改变
        boolean provinceCityChanged = !Objects.equals(oldProvince, newProvince) ||
                !Objects.equals(oldCity, newCity);

        if (!provinceCityChanged) {
            log.debug("⏭️ 跨表同步跳过：省市未发生变化");
            return false;
        }

        // 条件2：单位必须在上报单位表中存在
        if (!StringUtils.hasText(newUnit)) {
            log.debug("⏭️ 跨表同步跳过：单位名称为空");
            return false;
        }

        ReportUnit reportUnit = reportUnitMapper.selectByReportUnitName(newUnit);
        boolean unitExists = reportUnit != null;

        if (!unitExists) {
            log.debug("⏭️ 跨表同步跳过：单位不存在 - {}", newUnit);
            return false;
        }

        log.debug("✅ 满足跨表同步条件 - 单位: {}, 省市变化: {}-{} → {}-{}",
                newUnit, oldProvince, oldCity, newProvince, newCity);
        return true;
    }

    /**
     * 🔄 跨表同步到网信资产表

     * ==================== 方法说明 ====================
     * 将数据资产的省市变更同步到网信资产表中相同单位的记录。
     * 只同步省市字段，其他字段保持不变，确保数据一致性。

     * 🎯 同步方向：数据表 → 网信表
     *
     * @param reportUnit 上报单位名称
     * @param province 新的省份
     * @param city 新的城市
     */
    private void syncToCyberTable(String reportUnit, String province, String city) {
        try {
            CyberAsset updateEntity = new CyberAsset();
            updateEntity.setProvince(province);
            updateEntity.setCity(city);

            QueryWrapper<CyberAsset> wrapper = new QueryWrapper<>();
            wrapper.eq("report_unit", reportUnit);

            int updateCount = cyberAssetMapper.update(updateEntity, wrapper);
            log.info("✅ 跨表同步完成 - 网信表单位: {}, 更新记录数: {}, 新省市: {}-{}",
                    reportUnit, updateCount, province, city);
        } catch (Exception e) {
            log.error("❌ 跨表同步失败 - 单位: {}, 错误: {}", reportUnit, e.getMessage());
        }
    }

// ==================== 标准化和校验方法（优化版） ====================

    /**
     * 🎯 省市标准化处理（优化版）

     * ==================== 方法说明 ====================
     * 对用户输入的省市信息进行标准化处理，确保数据格式统一。
     * 新增：使用精确的标准化逻辑，避免误匹配。

     * ==================== 处理规则 ====================
     * 1. 省份标准化：使用精确的简称到全称映射
     * 2. 城市标准化：支持多种行政区划类型的标准化
     * 3. 格式统一：确保所有省市名称使用标准格式
     */
    private void standardizeProvinceCity(DataContentAsset asset) {
        String originalProvince = asset.getProvince();
        String originalCity = asset.getCity();

        // 🆕 优化：分别标准化省份和城市
        String standardizedProvince = standardizeProvinceName(originalProvince);
        if (!originalProvince.equals(standardizedProvince)) {
            log.debug("🏷️ 省份标准化: '{}' → '{}'", originalProvince, standardizedProvince);
            asset.setProvince(standardizedProvince);
        }

        String standardizedCity = standardizeCityName(originalCity);
        if (!originalCity.equals(standardizedCity)) {
            log.debug("🏷️ 城市标准化: '{}' → '{}'", originalCity, standardizedCity);
            asset.setCity(standardizedCity);
        }
    }

    /**
     * 🏷️ 省份名称标准化（优化版）

     * ==================== 方法说明 ====================
     * 使用精确的简称到全称映射，确保省份名称格式统一。
     * 避免使用包含匹配导致的误匹配问题。

     * @param provinceName 原始省份名称
     * @return 标准化后的省份名称
     */
    private String standardizeProvinceName(String provinceName) {
        if (!StringUtils.hasText(provinceName)) {
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
            log.debug("🏷️ 省份简称映射: '{}' → '{}'", provinceName, standardized);
            return standardized;
        }

        // 3. 使用简写匹配标准名称（兜底方案）
        for (String standardProvince : areaCacheTool.getAllProvinceNames()) {
            String standardAbbr = getProvinceAbbreviation(standardProvince);
            if (standardAbbr.equals(provinceName)) {
                log.debug("🏷️ 省份简写匹配: '{}' → '{}'", provinceName, standardProvince);
                return standardProvince;
            }
        }

        log.debug("⚠️ 无法标准化省份名称: {}", provinceName);
        return provinceName;
    }

    /**
     * 🏷️ 城市名称标准化（优化版）

     * ==================== 方法说明 ====================
     * 支持多种行政区划类型的标准化处理，确保城市名称格式统一。
     * 使用精确匹配，避免误匹配问题。

     * @param cityName 原始城市名称
     * @return 标准化后的城市名称
     */
    private String standardizeCityName(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            return cityName;
        }

        cityName = cityName.trim();

        // 1. 检查是否已经是标准城市名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            if (standardCity.equals(cityName)) {
                return cityName; // 已经是标准格式
            }
        }

        // 2. 使用简写匹配标准名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            String standardAbbr = getCityAbbreviation(standardCity);
            if (standardAbbr.equals(cityName)) {
                log.debug("🏷️ 城市简写匹配: '{}' → '{}'", cityName, standardCity);
                return standardCity;
            }
        }

        log.debug("⚠️ 无法标准化城市名称: {}", cityName);
        return cityName;
    }

    /**
     * 🆕 创建省份简称到全称的精确映射
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


//    /**
//     * 🔍 省市字段严格校验
//     */
//    private void validateProvinceCity(String province, String city) {
//        log.debug("🔍 开始省市字段校验 - 省: {}, 市: {}", province, city);
//
//        if (!StringUtils.hasText(province)) {
//            throw new RuntimeException("省份不能为空");
//        }
//
//        List<String> validProvinces = Arrays.asList(
//                "北京市", "天津市", "河北省", "山西省", "内蒙古自治区", "辽宁省", "吉林省", "黑龙江省",
//                "上海市", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省", "湖北省",
//                "湖南省", "广东省", "广西壮族自治区", "海南省", "重庆市", "四川省", "贵州省", "云南省",
//                "西藏自治区", "陕西省", "甘肃省", "青海省", "宁夏回族自治区", "新疆维吾尔自治区", "台湾省",
//                "香港特别行政区", "澳门特别行政区", "未知"
//        );
//
//        if (!validProvinces.contains(province)) {
//            throw new RuntimeException("省份必须是34个标准省份之一或'未知'，当前省份: " + province);
//        }
//
//        if (!StringUtils.hasText(city)) {
//            throw new RuntimeException("城市不能为空");
//        }
//
//        if (city.trim().isEmpty()) {
//            throw new RuntimeException("城市不能为纯空格");
//        }
//
//        log.debug("✅ 省市字段校验通过 - 省: {}, 市: {}", province, city);
//    }

    // ==================== 简写处理方法（完整版） ====================

    /**
     * 🏷️ 获取省份名称的简写形式

     * ==================== 方法说明 ====================
     * 从完整的省份名称中提取核心简写名称，便于匹配和标准化处理。
     * 支持所有类型的省级行政区划名称。

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
                .replace("市", "");
    }

    /**
     * 🏷️ 获取城市名称的简写形式（完整版）

     * ==================== 方法说明 ====================
     * 从完整的行政区划名称中提取核心简写名称，便于在单位名称中进行匹配。
     * 支持处理所有类型的行政区划，包括地级市、县级市、自治州、地区、盟、特别行政区等。

     * ==================== 处理规则 ====================
     * 1. 特殊自治州映射：对常见自治州使用习惯简写
     * 2. 后缀去除规则：按行政区划类型去除相应后缀
     *    - 市：去除"市"后缀
     *    - 自治州：去除"自治州"后缀
     *    - 地区：去除"地区"后缀
     *    - 盟：去除"盟"后缀
     *    - 特别行政区：去除"特别行政区"后缀

     * @param city 完整的城市/行政区划名称
     * @return 处理后的简写名称，如无法处理则返回原名称
     */
    private String getCityAbbreviation(String city) {
        // 1. 空值检查：确保输入有效
        if (!StringUtils.hasText(city)) {
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

        // 检查特殊映射
        if (specialAutonomousMapping.containsKey(city)) {
            String abbreviation = specialAutonomousMapping.get(city);
            log.debug("🔤 特殊自治州简写映射: '{}' -> '{}'", city, abbreviation);
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
        log.debug("⚠️ 无法简写的城市名称: '{}'，保持原值", city);
        return city;
    }

    // ==================== 其他业务方法 ====================

    /**
     * ✅ 统一的业务字段校验方法

     * ==================== 数据资产特有字段 ====================
     * - 应用领域：必填，固定选项
     * - 开发工具：必填，固定选项
     * - 更新周期：必填，固定选项
     * - 更新方式：必填，固定选项
     */
    private void validateBusinessFields(DataContentAsset asset) {
        validateReportUnit(asset);
        validateCategory(asset);
        validateAssetName(asset);
        validateApplicationField(asset);
        validateDevelopmentTool(asset);
        validateActualQuantity(asset);
        validateUnit(asset);
        validateUnitPrice(asset);
        validateUpdateCycle(asset);
        validateUpdateMethod(asset);
        validateInventoryUnit(asset);
    }

    /**
     * 删除数据内容资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于删除单条数据内容资产记录，包含资产存在性校验、数据删除和上报单位表同步功能。
     * 数据内容资产表与其他资产表的主要区别：有省市字段，需要同时维护自身字段和上报单位表。

     * 核心流程：
     * 1. 资产存在性校验阶段 → 2. 数据删除阶段 → 3. 上报单位表同步阶段

     * 业务规则：
     * - 必须先查询资产是否存在，获取完整的资产信息（包括省市）
     * - 删除操作必须同步更新上报单位表的状态标志
     * - 使用事务确保数据一致性，任何步骤失败都会回滚

     * 同步逻辑：
     * - 调用 provinceAutoFillTool.syncReportUnit 方法
     * - 设置 isDelete=true，表示删除场景
     * - 如果该单位不再有数据资产，系统会自动将数据资产状态标志设为0
     * - 使用资产中的省份信息进行同步，确保数据准确性

     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或删除失败都会回滚整个事务
     * - rollbackFor = Exception.class 确保所有异常都会触发回滚

     * 适用场景：
     * - 前端手动删除数据内容资产
     * - 需要完整事务管理和上报单位同步的业务场景
     * - 单条记录删除操作

     * 注意事项：
     * - 删除前必须查询资产信息，获取上报单位名称和省市信息用于同步
     * - 删除后需要同步上报单位表，确保状态标志准确
     * - 如果资产不存在，抛出明确的业务异常信息
     * - 数据资产有省市字段，同步时需要传递省份参数
     *
     * @param id 数据内容资产主键ID，必填参数
     * @throws RuntimeException 当资产不存在或删除失败时抛出业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(String id) {
        log.info("🚀 开始删除数据内容资产，ID：{}", id);

        // ==================== 1. 资产存在性校验阶段 ====================

        // 1.1 根据ID查询资产信息（包含省市字段）
        DataContentAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            log.error("❌ 数据内容资产不存在，删除失败，ID：{}", id);
            throw new RuntimeException("数据内容资产不存在，ID：" + id);
        }

        // 1.2 获取上报单位和省市信息，用于后续同步操作
        String reportUnit = asset.getReportUnit();
        String province = asset.getProvince();
        log.debug("📋 找到待删除数据内容资产 - ID: {}, 上报单位: {}, 省份: {}, 资产名称: {}",
                id, reportUnit, province, asset.getAssetName());

        // ==================== 2. 数据删除阶段 ====================

        // 2.1 执行物理删除操作
        int deleteCount = baseMapper.deleteById(id);
        if (deleteCount == 0) {
            log.error("❌ 数据内容资产删除失败，可能已被其他操作删除，ID：{}", id);
            throw new RuntimeException("删除数据内容资产失败，ID：" + id);
        }

        log.info("✅ 删除数据内容资产成功，ID：{}，资产名称：{}", id, asset.getAssetName());

        // ==================== 3. 上报单位表同步阶段 ====================

        // 3.1 同步上报单位表状态（删除场景）
        // 作用：更新上报单位表中该单位的数据资产状态标志
        // 逻辑：如果该单位不再有数据资产，系统会自动将data_content_asset_status设为0
        provinceAutoFillTool.syncReportUnit(
                reportUnit,           // 上报单位名称（从已删除资产获取）
                province,             // 数据资产有省份字段，使用资产中的省份信息
                "dataContent",        // 资产类型：数据内容资产
                true                  // isDelete=true：删除场景，触发状态标志更新
        );
        log.debug("🔄 数据内容资产删除完成，已同步上报单位表状态 - 单位: {}, 省份: {}", reportUnit, province);
    }

    @Override
    public boolean checkCategoryMatch(String categoryCode, String assetCategory) {
        if (!StringUtils.hasText(categoryCode) || !StringUtils.hasText(assetCategory)) {
            return false;
        }
        String legalCategory = CATEGORY_MAP.get(categoryCode.trim());
        if (!StringUtils.hasText(legalCategory)) {
            return false;
        }
        return legalCategory.trim().equals(assetCategory.trim());
    }

    @Override
    public void validateDevelopmentTool(String developmentTool) {
        if (developmentTool == null || developmentTool.trim().isEmpty()) {
            throw new RuntimeException("数据资产开发工具不能为空（特有字段）");
        }
    }

    @Override
    public BigDecimal calculateProvinceInformationDegree(String province) {
        validateProvince(province);
        long totalQuantity = sumActualQuantity(
                lambdaQuery()
                        .select(DataContentAsset::getActualQuantity)
                        .list()
        );
        if (totalQuantity <= 0) {
            log.info("当前系统暂无数据内容资产，信息化程度默认为0");
            return DataContentAssetMetricsUtils.calculateInformationDegree(0, 0);
        }
        long provinceQuantity = sumActualQuantity(
                lambdaQuery()
                        .select(DataContentAsset::getActualQuantity)
                        .eq(DataContentAsset::getProvince, province)
                        .list()
        );
        BigDecimal degree = DataContentAssetMetricsUtils.calculateInformationDegree(provinceQuantity, totalQuantity);
        log.info("省份{}信息化程度计算完成：{} (省份总量：{}，全部总量：{})", province, degree, provinceQuantity, totalQuantity);
        return degree;
    }

    @Override
    public BigDecimal calculateProvinceDomesticRate(String province) {
        validateProvince(province);
        long provinceQuantity = sumActualQuantity(
                lambdaQuery()
                        .select(DataContentAsset::getActualQuantity)
                        .eq(DataContentAsset::getProvince, province)
                        .list()
        );
        if (provinceQuantity <= 0) {
            log.info("省份{}暂无数据内容资产，国产化率默认为0", province);
            return DataContentAssetMetricsUtils.calculateDomesticRate(0, 0);
        }
        long domesticQuantity = sumActualQuantity(
                lambdaQuery()
                        .select(DataContentAsset::getActualQuantity)
                        .eq(DataContentAsset::getProvince, province)
                        .in(DataContentAsset::getDevelopmentTool, DataContentAssetMetricsUtils.getDomesticDevelopmentTools())
                        .list()
        );
        BigDecimal rate = DataContentAssetMetricsUtils.calculateDomesticRate(domesticQuantity, provinceQuantity);
        log.info("省份{}国产化率计算完成：{} (国产工具总量：{}，省份总量：{})", province, rate, domesticQuantity, provinceQuantity);
        return rate;
    }

    // ============================ 私有工具方法 ============================

    /**
     * 校验资产ID格式（数字+字母组合，移除长度限制）
     */
    @SuppressWarnings("all")
    private boolean isValidAssetId(String id) {
        if (!StringUtils.hasText(id)) {
            return false;
        }

        // 只允许数字和字母，移除长度限制
        return id.matches("^[a-zA-Z0-9]+$");
    }

    private void validateProvince(String province) {
        if (!StringUtils.hasText(province)) {
            throw new RuntimeException("省份不能为空");
        }
    }

    private long sumActualQuantity(List<DataContentAsset> assets) {
        return assets.stream()
                .map(DataContentAsset::getActualQuantity)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    // ============================ 新增方法实现（接口方法） ============================

    @Override
    public void addDataAsset(DataContentAsset asset) {
        // 直接调用原有的 add 方法，因为 add 方法已经集成了省市自动填充和上报单位同步
        add(asset);
        log.debug("通过 addDataAsset 方法新增数据内容资产成功，ID：{}", asset.getId());
    }

    @Override
    public void updateDataAsset(DataContentAsset asset) {
        // 直接调用原有的 update 方法，因为 update 方法已经集成了省市自动填充和上报单位同步
        update(asset);
        log.debug("通过 updateDataAsset 方法修改数据内容资产成功，ID：{}", asset.getId());
    }

    @Override
    public void deleteDataAsset(String id) {
        // 直接调用原有的 remove 方法，因为 remove 方法已经集成了上报单位同步
        remove(id);
        log.debug("通过 deleteDataAsset 方法删除数据内容资产成功，ID：{}", id);
    }

    // ============================ 新增额外接口 ============================
    // 接口1：统计数据内容资产数量
    @Override
    public long count() {
        // 使用MyBatis-Plus的count方法
        return this.getBaseMapper().selectCount(null);
    }

    // 接口2：快速查询接口
    @Override
    public Page<DataContentAsset> queryByApplicationField(Page<DataContentAsset> page, String applicationField) {
        return this.getBaseMapper().queryByApplicationField(page, applicationField);
    }

    /**
     * 接口3
     * 实现按上报单位查询数据内容资产
     * 调用Mapper层的queryByReportUnit方法执行SQL查询
     */
    @Override
    public Page<DataContentAsset> queryByReportUnit(Page<DataContentAsset> page, String reportUnit) {
        return this.getBaseMapper().queryByReportUnit(page, reportUnit);
    }

// ==================== 新增：接口4相关方法实现 ====================

    @Override
    public List<Map<String, Object>> getProvinceUnitStats() {
        /**
         * 实现数据内容资产表省份单位统计（新逻辑：关联report_unit表）

         * 设计考虑：为了保持三个资产表统计方法的一致性
         * 统一通过关联report_unit表获取省份信息

         * SQL执行逻辑：
         *   SELECT ru.province, COUNT(DISTINCT dca.report_unit) as count
         *   FROM data_content_asset dca
         *   INNER JOIN report_unit ru ON dca.report_unit = ru.report_unit
         *   WHERE ru.province IS NOT NULL AND ru.province != ''
         *   GROUP BY ru.province
         *   ORDER BY count DESC

         * 优势：
         * - 统一数据源，避免因数据录入错误导致统计偏差
         * - report_unit表的province字段经过专门维护，更加准确
         * - 便于后续维护和扩展
         */
        return this.getBaseMapper().selectProvinceUnitStats();
    }

// ============================ 🆕 新增方法（清空再导入专用） ============================

    /**
     * 清空数据内容资产表并重置上报单位表状态（导入专用）
     * 🎯 核心操作：
     * 1. 清空data_content_asset表的所有数据
     * 2. 将report_unit表中source_table_data_content_asset字段全部设为0

     * 💡 重要说明：
     * - 只重置数据内容资产状态，不影响其他资产表的状态
     * - 不清空report_unit表的其他字段（省市信息等）
     * - 使用事务确保数据一致性

     * 🚨 风险提示：
     * - 此操作会永久删除所有数据内容资产数据
     * - 只能在导入前调用，确保数据备份
     *
     * @throws RuntimeException 当清空操作失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearDataContentTableAndResetStatus() {
        log.info("🗑️ 开始清空数据内容资产表并重置上报单位表状态...");

        try {
            // 1. 清空data_content_asset表的所有数据
            int deletedCount = baseMapper.delete(null);
            log.info("✅ 清空数据内容资产表完成，共删除{}条记录", deletedCount);

            // 2. 重置report_unit表中数据内容资产状态为0
            int updatedCount = baseMapper.resetDataContentAssetStatus();
            log.info("✅ 重置上报单位表数据内容资产状态完成，共更新{}条记录", updatedCount);

            log.info("🎉 数据内容资产表和状态重置完成");
        } catch (Exception e) {
            log.error("❌ 清空数据内容资产表失败: {}", e.getMessage(), e);
            throw new RuntimeException("清空数据内容资产表失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveForImport(List<DataContentAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            log.info("ℹ️ 批量保存数据内容资产：无数据需要保存");
            return;
        }

        log.info("💾 开始批量保存数据内容资产并同步省市信息，共{}条数据", assets.size());

        try {
            // 1. 批量处理省市信息
            processProvinceCityForBatch(assets);

            // 2. 批量保存到data_content_asset表
            boolean saveResult = saveBatch(assets);
            if (!saveResult) {
                throw new RuntimeException("批量保存数据内容资产失败");
            }
            log.info("✅ 批量保存数据内容资产成功，共{}条", assets.size());

            // 3. 按上报单位分组，用于批量同步
            Map<String, List<DataContentAsset>> unitGroupedAssets = assets.stream()
                    .collect(Collectors.groupingBy(DataContentAsset::getReportUnit));

            log.info("📊 按单位分组完成，共{}个不同单位", unitGroupedAssets.size());

            // 4. 批量同步上报单位表
            List<ProvinceAutoFillTool.UnitSyncRequest> syncRequests = new ArrayList<>();
            for (Map.Entry<String, List<DataContentAsset>> entry : unitGroupedAssets.entrySet()) {
                String unitName = entry.getKey();
                DataContentAsset firstAsset = entry.getValue().get(0);
                syncRequests.add(new ProvinceAutoFillTool.UnitSyncRequest(
                        unitName,
                        firstAsset.getProvince(),
                        "dataContent",
                        false
                ));
            }

            // 执行批量同步
            provinceAutoFillTool.batchSyncReportUnits(syncRequests);

            log.info("✅ 数据内容资产批量导入完成，省市信息同步完成，涉及{}个单位", unitGroupedAssets.size());

        } catch (Exception e) {
            log.error("❌ 批量保存数据内容资产失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量保存数据内容资产失败: " + e.getMessage());
        }
    }

    /**
     * 批量处理省市信息（数据内容资产）- 最简版本
     * 🎯 移除场景统计，专注于核心功能
     */
    private void processProvinceCityForBatch(List<DataContentAsset> assets) {
        log.info("🔄 开始批量处理数据内容资产省市信息，共{}条数据", assets.size());

        for (DataContentAsset asset : assets) {
            // 直接调用自动填充逻辑
            provinceAutoFillTool.fillAssetProvinceCity(asset, false);
        }

        log.info("✅ 批量处理数据内容资产省市信息完成");
    }

    /**
     * 数据资产导出查询方法实现
     * 作用：根据前端传递的动态条件查询数据资产数据，用于导出功能
     * 特点：
     * - 支持任意条件组合，所有参数都是可选的
     * - 不分页查询，返回所有匹配的数据
     * - 复用现有的联合查询逻辑，确保查询条件一致性
     * - 包含完整的日志记录，便于问题排查和系统监控

     * 参数说明：
     * @param reportUnit 上报单位（可选）- 按单位筛选
     * @param province 省份（可选）- 按省份筛选
     * @param city 城市（可选）- 按城市筛选
     * @param applicationField 应用领域（可选）- 按应用领域筛选
     * @param developmentTool 开发工具（可选）- 按开发工具筛选
     * @param quantityMin 实有数量最小值（可选）- 数量范围查询
     * @param quantityMax 实有数量最大值（可选）- 数量范围查询
     * @param updateCycle 更新周期（可选）- 按更新周期筛选
     * @param updateMethod 更新方式（可选）- 按更新方式筛选
     * @param inventoryUnit 盘点单位（可选）- 按盘点单位筛选
     *
     * @return List<DataContentAsset> 返回所有匹配的数据资产数据列表
     * 技术实现：
     * - 使用超大分页(1, Integer.MAX_VALUE)获取所有数据
     * - 复用combinedQuery方法，避免重复代码
     * - 动态条件处理由combinedQuery内部实现
     * - 完整的异常处理和日志记录
     *
     * 技术细节：由于combinedQuery返回Object类型，需要进行强制类型转换
     */
    /**
     * 数据资产联合查询方法实现
     * 作用：根据动态条件分页查询数据资产数据
     * 注意：使用Java原生字符串判断，避免额外依赖
     */
    @Override
    public Page<DataContentAsset> combinedQuery(Page<DataContentAsset> pageInfo,
                                                String reportUnit, String province, String city,
                                                String applicationField, String developmentTool, Integer quantityMin,
                                                Integer quantityMax, String updateCycle, String updateMethod,
                                                String inventoryUnit) {
        try {
            log.info("执行数据资产联合查询 - 条件: reportUnit={}, province={}, city={}",
                    reportUnit, province, city);

            // 构建查询条件
            QueryWrapper<DataContentAsset> queryWrapper = new QueryWrapper<>();

            // 动态添加查询条件 - 使用Java原生字符串判断
            if (reportUnit != null && !reportUnit.trim().isEmpty()) {
                queryWrapper.like("report_unit", reportUnit);
            }
            if (province != null && !province.trim().isEmpty()) {
                queryWrapper.eq("province", province);
            }
            if (city != null && !city.trim().isEmpty()) {
                queryWrapper.eq("city", city);
            }
            if (applicationField != null && !applicationField.trim().isEmpty()) {
                queryWrapper.like("application_field", applicationField);
            }
            if (developmentTool != null && !developmentTool.trim().isEmpty()) {
                queryWrapper.like("development_tool", developmentTool);
            }
            if (quantityMin != null) {
                queryWrapper.ge("actual_quantity", quantityMin);
            }
            if (quantityMax != null) {
                queryWrapper.le("actual_quantity", quantityMax);
            }
            if (updateCycle != null && !updateCycle.trim().isEmpty()) {
                queryWrapper.eq("update_cycle", updateCycle);
            }
            if (updateMethod != null && !updateMethod.trim().isEmpty()) {
                queryWrapper.eq("update_method", updateMethod);
            }
            if (inventoryUnit != null && !inventoryUnit.trim().isEmpty()) {
                queryWrapper.like("inventory_unit", inventoryUnit);
            }

            // 执行分页查询
            Page<DataContentAsset> result = baseMapper.selectPage(pageInfo, queryWrapper);
            log.info("数据资产联合查询完成，共{}条数据", result.getRecords().size());

            return result;

        } catch (Exception e) {
            log.error("数据资产联合查询失败", e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    // ============================== 新增：各省份资产统计方法 ==============================
    /**
     * 获取各省份数据资产统计概览
     * 作用：统计34个省份+"未知"的数据资产数量和占比

     * 核心逻辑：
     * 1. 直接使用数据资产表的province字段进行统计
     * 2. 统计每个省份的数据资产数量
     * 3. 计算每个省份数据资产占总量的百分比
     * 4. 包含"未知"省份的统计

     * 技术特点：
     * - 数据资产表有独立的province字段，无需关联查询
     * - 使用COALESCE处理null值，确保统计完整性
     * - 支持"未知"省份的准确统计
     *
     * @return 包含总数量和各省份统计的结果
     */
    @Override
    public Map<String, Object> getProvinceAssetOverview() {
        log.info("开始统计各省份数据资产数量和占比...");

        Map<String, Object> result = new HashMap<>();

        // 1. 获取数据资产总数
        long totalDataContentCount = baseMapper.selectCount(null);
        result.put("totalDataContentCount", totalDataContentCount);

        // 2. 获取各省份数据资产统计
        List<Map<String, Object>> provinceStats = baseMapper.selectProvinceDataContentStats();

        // 3. 转换为前端需要的格式并计算百分比
        List<Map<String, Object>> formattedStats = new ArrayList<>();
        for (Map<String, Object> stat : provinceStats) {
            String province = (String) stat.get("province");
            Long count = (Long) stat.get("count");

            Map<String, Object> formattedStat = new HashMap<>();
            formattedStat.put("province", province != null ? province : "未知");  // 修改：将"其他"改为"未知"
            formattedStat.put("dataContentCount", count);

            // 计算百分比
            double percentage = totalDataContentCount > 0 ?
                    (count.doubleValue() / totalDataContentCount) * 100 : 0.0;
            formattedStat.put("dataContentPercentage", Math.round(percentage * 10.0) / 10.0);

            formattedStats.add(formattedStat);
        }

        result.put("dataContentProvinceStats", formattedStats);
        log.info("数据资产省份统计完成 - 总数: {}, 省份数量: {}", totalDataContentCount, formattedStats.size());

        return result;
    }

    /**
     * 获取指定省份数据资产的资产分类细分
     * 作用：统计指定省份下各数据资产分类的数量和占比，确保返回完整的固定分类列表

     * 核心业务逻辑：
     * 1. 查询该省份数据资产总数
     * 2. 查询该省份各分类的实际统计数据
     * 3. 初始化数据资产固定分类映射表（只有一个分类）
     * 4. 创建包含所有固定分类的统计结果，默认数量为0
     * 5. 用实际查询结果更新对应分类的数量
     * 6. 计算各分类在该省份中的占比
     * 7. 返回完整的分类细分统计结果

     * 特殊说明：
     * - 数据内容资产只有一个固定分类"数据内容资产"
     * - 为了保持接口一致性，仍然使用相同的返回结构
     * - 如果将来扩展更多数据资产分类，只需在此添加映射即可
     * - 数据资产表有独立的province字段，无需关联查询
     *
     * @param province 省份名称
     * @return 包含分类细分的统计结果
     */
    @Override
    public Map<String, Object> getProvinceAssetCategoryDetail(String province) {
        log.info("开始统计省份数据资产分类细分 - 省份: {}", province);

        Map<String, Object> result = new HashMap<>();
        result.put("province", province);
        result.put("assetType", "data");  // 使用统一的资产类型标识

        // 1. 获取该省份数据资产总数
        Long provinceTotalCount = baseMapper.selectDataContentCountByProvince(province);
        if (provinceTotalCount == null) provinceTotalCount = 0L;
        result.put("totalCount", provinceTotalCount);
        log.debug("省份数据资产总数统计完成 - 省份: {}, 总数: {}", province, provinceTotalCount);

        // 2. 获取该省份各资产分类的实际统计数据
        List<Map<String, Object>> categoryStats = baseMapper.selectDataContentCategoryStatsByProvince(province);
        log.debug("获取到{}条数据资产分类统计记录", categoryStats.size());

        // 3. 定义所有数据资产分类的固定列表（使用分类名称作为标识）
        // 目前数据内容资产只有一个分类，但使用相同结构便于将来扩展
        List<String> allCategoryNames = Arrays.asList("数据内容资产");

        // 4. 创建分类统计映射，初始化所有分类数量为0
        Map<String, Long> categoryCountMap = new LinkedHashMap<>(); // 使用LinkedHashMap保持顺序
        for (String categoryName : allCategoryNames) {
            categoryCountMap.put(categoryName, 0L);
        }
        log.debug("初始化了{}个数据资产分类", categoryCountMap.size());

        // 5. 填充实际统计数据
        for (Map<String, Object> stat : categoryStats) {
            String categoryName = (String) stat.get("asset_category"); // 直接获取分类名称
            Long count = (Long) stat.get("count");

            log.debug("处理数据资产分类统计 - 分类名称: {}, 数量: {}", categoryName, count);

            if (categoryName != null && categoryCountMap.containsKey(categoryName)) {
                categoryCountMap.put(categoryName, count);
                log.debug("成功更新数据资产分类统计 - 分类: {}, 数量: {}", categoryName, count);
            } else {
                log.warn("未知的数据资产分类名称: {}，已忽略", categoryName);
            }
        }

        // 6. 转换为前端需要的格式并计算百分比
        List<Map<String, Object>> formattedStats = new ArrayList<>();
        for (Map.Entry<String, Long> entry : categoryCountMap.entrySet()) {
            String categoryName = entry.getKey();
            Long count = entry.getValue();

            Map<String, Object> formattedStat = new HashMap<>();
            formattedStat.put("categoryName", categoryName);
            formattedStat.put("count", count);

            double percentage = provinceTotalCount > 0 ?
                    (count.doubleValue() / provinceTotalCount) * 100 : 0.0;
            formattedStat.put("percentage", Math.round(percentage * 10.0) / 10.0);

            formattedStats.add(formattedStat);
        }

        result.put("categoryStats", formattedStats);
        log.info("数据资产分类细分统计完成 - 省份: {}, 总数: {}, 分类数: {}",
                province, provinceTotalCount, formattedStats.size());

        return result;
    }

    /**
     * 根据应用领域按省份统计数据资产数量
     * 核心逻辑：
     * 1. 数据资产表有自身的province字段，可以直接使用该字段进行统计
     * 2. 由于数据资产表的资产分类只有"数据内容资产"一个值，按应用领域统计更有业务意义
     * 3. 统计指定应用领域下各省份的资产数量分布
     * 4. 处理省份为空的情况，统一归类为"未知"省份

     * 业务背景：
     * - 数据资产表的资产分类字段值固定为"数据内容资产"，缺乏分类区分度
     * - 应用领域字段具有更好的业务分类价值，如"后勤保障"、"作战指挥"等
     * - 按应用领域统计能更好反映数据资产的功能分布
     *
     * @param applicationField 应用领域名称，必须是有效的领域（如"后勤保障"、"作战指挥"等）
     * @return Map<String, Long> 省份-数量映射，key为省份名称，value为该省份的资产数量
     * @throws RuntimeException 当统计过程中发生数据库异常或其他系统异常时抛出

     * 示例返回：
     * {
     *   "北京市": 30,
     *   "江苏省": 15,
     *   "四川省": 8,
     *   "未知": 1
     * }
     */
    @Override
    public Map<String, Long> getProvinceStatsByApplicationField(String applicationField) {
        try {
            log.info("开始按应用领域统计数据资产省份分布 - applicationField: {}", applicationField);

            // 参数校验
            if (applicationField == null || applicationField.trim().isEmpty()) {
                log.warn("应用领域参数为空，无法进行统计");
                return Collections.emptyMap();
            }

            // 使用数据资产表自身的province字段，按应用领域统计
            List<Map<String, Object>> stats = dataContentAssetMapper.selectProvinceStatsByApplicationField(applicationField);

            Map<String, Long> result = new HashMap<>();
            for (Map<String, Object> stat : stats) {
                String province = (String) stat.get("province");
                Long count = (Long) stat.get("count");

                // 处理省份为null或空字符串的情况，统一转为"未知"
                // 考虑因素：确保统计结果的完整性，不遗漏任何记录
                if (province == null || province.trim().isEmpty()) {
                    province = "未知";
                }
                result.put(province, count);
            }

            log.info("按应用领域统计数据资产省份分布完成 - applicationField: {}, 统计省份数: {}",
                    applicationField, result.size());
            return result;
        } catch (Exception e) {
            log.error("按应用领域统计数据资产省份分布失败 - applicationField: {}", applicationField, e);
            throw new RuntimeException("统计失败：" + e.getMessage());
        }
    }
}