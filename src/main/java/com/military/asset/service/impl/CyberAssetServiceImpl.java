package com.military.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.military.asset.entity.CyberAsset;
import com.military.asset.mapper.CyberAssetMapper;
import com.military.asset.service.CyberAssetService;
import com.military.asset.utils.CategoryMapUtils;
import com.military.asset.utils.ProvinceAutoFillTool; // 新增：导入自动填充工具
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 确保导入Page类
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.excel.CyberAssetExcelVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.math.BigDecimal;
import jakarta.annotation.Resource; // 新增：资源注入注解
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Arrays;

//导出功能依赖
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;


/**
 * 网信资产业务实现类
 * 完全遵循软件资产服务层结构，适配网信特有约束（已用数量≤实有数量）
 * 继承ServiceImpl自动获取baseMapper，无需手动注入
 * getExistingAssetsMap(): 实现完整资产对象Map的加载，用于导入时关键字段比较

 * 新增功能：
 * - 省市自动填充：集成ProvinceAutoFillTool实现省市字段自动填充
 * - 上报单位表同步：在增删改操作中同步上报单位表状态
 */
@Service
@Slf4j
@SuppressWarnings("unused")
public class CyberAssetServiceImpl extends ServiceImpl<CyberAssetMapper, CyberAsset> implements CyberAssetService {

    /**
     * 网信资产分类映射表：从工具类获取标准编码-分类对应关系
     */
    private final Map<String, String> CATEGORY_MAP = CategoryMapUtils.initCyberCategoryMap();

    /**
//     * 最大有效年限：业务规则限定投入使用日期不能早于当前76年（115修改不需要了！）
//     */
//    private static final int MAX_VALID_YEARS = 76;

    // ============================ 新增依赖注入 ============================

    /**
     * 省市自动填充工具：负责处理省市字段的自动填充逻辑
     * 支持场景：Excel有值优先、填省补首府、填市补省、修改上报单位同步
     */
    @Resource
    private ProvinceAutoFillTool provinceAutoFillTool;

    // ============================ 新增方法实现 ============================

    @Override
    public Map<String, CyberAsset> getExistingAssetsMap() {
        try {
            // 查询所有已存在的网信资产（完整对象）
            List<CyberAsset> existingAssets = baseMapper.selectAllExistingAssets();

            // 转换为Map结构，键为资产ID，值为完整资产对象
            // 使用Collectors.toMap提供O(1)的查询性能
            Map<String, CyberAsset> assetsMap = existingAssets.stream()
                    .collect(Collectors.toMap(
                            CyberAsset::getId,  // 键：资产ID
                            asset -> asset,     // 值：完整资产对象
                            (existing, replacement) -> existing  // 冲突处理：保留现有值
                    ));

            log.info("成功加载{}条网信资产到内存Map，用于导入时关键字段比较", assetsMap.size());
            return assetsMap;

        } catch (Exception e) {
            log.error("加载网信资产Map失败，无法进行关键字段比较", e);
            throw new RuntimeException("加载资产数据失败: " + e.getMessage());
        }
    }

    // ============================ 原有方法实现（添加省市自动填充和上报单位同步） ============================


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveCyberAssets(List<CyberAssetExcelVO> validDataList) {
        // 调用原有的 batchSaveValidData 方法
        batchSaveValidData(validDataList);
    }

    @Override
    public List<String> getExistingIds() {
        try {
            List<String> ids = baseMapper.selectAllExistingIds();
            log.info("查询网信资产已存在ID完成，共{}条记录", ids.size());
            return ids;
        } catch (Exception e) {
            log.error("查询网信资产ID列表失败", e);
            throw new RuntimeException("查询ID失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveValidData(List<CyberAssetExcelVO> validVoList) {
        if (validVoList.isEmpty()) {
            log.info("无合法网信资产数据需保存，跳过入库");
            return;
        }

        List<CyberAsset> entities = new ArrayList<>();
        for (CyberAssetExcelVO vo : validVoList) {
            CyberAsset entity = new CyberAsset();
            BeanUtils.copyProperties(vo, entity);
            entity.setCreateTime(LocalDateTime.now());

            // ============ 新增：省市自动填充（Excel导入场景） ============
            // 调用自动填充工具，isUpdate=false表示Excel导入场景
            // 处理逻辑：Excel有值优先 → 部分缺失补全 → 无值则按上报单位推导
            provinceAutoFillTool.fillAssetProvinceCity(entity, false);
            log.debug("网信资产导入自动填充省市：ID={}, 单位={}, 省={}, 市={}",
                    entity.getId(), entity.getReportUnit(), entity.getProvince(), entity.getCity());
// ============ 测试导入 =======================   ============ ============  ============ ============ ============ ============
//            System.out.println("=== 开始处理资产 ID: " + entity.getId() + " ===");
//            System.out.println("填充前 - 省: '" + entity.getProvince() + "', 市: '" + entity.getCity() + "', 单位: '" + entity.getReportUnit() + "'");
//
//            provinceAutoFillTool.fillAssetProvinceCity(entity, false);
//
//            System.out.println("填充后 - 省: '" + entity.getProvince() + "', 市: '" + entity.getCity() + "'");
//            System.out.println("=== 处理完成，准备保存 ===");
// ============ 测试导入 ============ ============ ============ ============ ============   ============ ============ ============ ============
            // ============ 新增结束 ============
            entities.add(entity);
        }

        baseMapper.insertBatch(entities);
        log.info("网信资产批量入库成功，共{}条记录", entities.size());

        // ============ 新增：上报单位表同步（批量导入场景） ============
        // 遍历所有成功保存的实体，同步上报单位表状态
        for (CyberAsset entity : entities) {
            provinceAutoFillTool.syncReportUnit(
                    entity.getReportUnit(),  // 上报单位名称
                    entity.getProvince(),    // 填充后的省份
                    "cyber",                 // 资产类型：网信
                    false                    // isDelete=false：新增场景
            );
        }
        log.info("网信资产批量导入完成，已同步上报单位表状态");
    }

    @Override
    public void handleImportResult(int totalRow, int validRow, List<ExcelErrorVO> errorList) {
        log.info("==== 网信资产Excel导入结果 ====");
        log.info("总记录数：{} | 成功入库：{}条 | 校验失败：{}条", totalRow, validRow, errorList.size());
        if (!errorList.isEmpty()) {
            log.warn("导入错误详情：");
            errorList.forEach(error ->
                    log.warn("行号：{} | 错误字段：{} | 原因：{}",
                            error.getExcelRowNum(), error.getErrorFields(), error.getErrorMsg())
            );
        }
    }

    @Override
    public CyberAsset getById(String id) {
        // 移除32位长度限制，只检查非空和格式
        if (!StringUtils.hasText(id) || !isValidAssetId(id)) {
            throw new RuntimeException("网信资产ID格式错误，必须由字母和数字组成");
        }

        CyberAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            throw new RuntimeException("未找到ID为" + id + "的网信资产");
        }
        log.info("查询网信资产详情成功，ID：{}", id);
        return asset;
    }

    // ====================== 网信基础资产联合查询方法实现（支持数量范围查询） ======================
    @Override
    public Object combinedQuery(Integer pageNum, Integer pageSize,
                                String reportUnit, String province, String city,
                                String categoryCode, String assetCategory,
                                Integer quantityMin, Integer quantityMax,
                                Integer usedQuantityMin, Integer usedQuantityMax,
                                String startUseDateStart, String startUseDateEnd, String inventoryUnit) {
        try {
            log.info("执行网信基础资产联合查询：pageNum={}, pageSize={}, reportUnit={}, province={}, city={}, " +
                            "categoryCode={}, assetCategory={}, quantityMin={}, quantityMax={}, usedQuantityMin={}, usedQuantityMax={}, " +
                            "startUseDateStart={}, startUseDateEnd={}, inventoryUnit={}",
                    pageNum, pageSize, reportUnit, province, city, categoryCode,
                    assetCategory, quantityMin, quantityMax, usedQuantityMin, usedQuantityMax,
                    startUseDateStart, startUseDateEnd, inventoryUnit);

            // 创建分页对象，使用MyBatis-Plus的分页功能
            Page<CyberAsset> page = new Page<>(pageNum, pageSize);

            // 调用Mapper进行联合查询
            Page<CyberAsset> resultPage = baseMapper.combinedQuery(
                    page, reportUnit, province, city, categoryCode,
                    assetCategory, quantityMin, quantityMax, usedQuantityMin, usedQuantityMax,
                    startUseDateStart, startUseDateEnd, inventoryUnit
            );

            log.info("网信基础资产联合查询完成，共查询到{}条记录，分{}页显示",
                    resultPage.getTotal(), resultPage.getPages());
            return resultPage;

        } catch (Exception e) {
            log.error("网信基础资产联合查询执行失败", e);
            throw new RuntimeException("联合查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 新增网信基础资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于新增单条网信资产记录，包含完整的数据校验、业务处理、数据保存和上报单位表同步功能。
     * 网信资产表与其他资产表的主要区别：有省市字段，需要同时维护自身省市字段和上报单位表。
     * 核心流程：
     * 1. 自动填充省市阶段 → 2. 数据校验阶段 → 3. 数据处理阶段 → 4. 数据保存阶段 → 5. 上报单位表同步阶段

     * 数据校验规则（按字段顺序）：
     * 1.1 主键：必填，数字字母组合，确保唯一性
     * 1.2 上报单位：必填字段
     * 1.3 分类编码与资产分类：必填，严格匹配预设映射关系
     * 1.4 资产名称：必填字段
     * 1.5 资产内容：必填字段，直接写出资产内容的信息
     * 1.6 实有数量：必填，非负整数（支持0）
     * 1.7 计量单位：必填字段
     * 1.8 单价：可选字段，如果填写则必须为非负数
     * 1.9 投入使用日期：必填，必须≥1949-10-01且≤当前日期
     * 1.10 已用数量：必填，非负整数且≤实有数量
     * 1.11 盘点单位：必填字段

     * 特殊处理逻辑：
     * - 省市自动填充：根据上报单位名称自动推导省市信息
     * - 金额字段：如果金额为空，且单价和实有数量都存在，则自动计算金额（单价×数量）
     * - 创建时间：系统自动生成当前时间
     * - 上报单位同步：使用填充后的省市信息同步到上报单位表
     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或保存失败都会回滚整个事务
     * 适用场景：
     * - 前端手动新增网信资产
     * - 需要完整校验和上报单位同步的业务场景
     * - 单条记录新增操作
     * 注意事项：
     * - 资产内容需要直接写出资产内容的信息，如IP地址范围段、频谱范围段等
     * - 已用数量必须小于等于实有数量
     * - 分类编码与资产分类必须严格匹配预设映射，否则校验失败
     * - 投入使用日期有严格的时间范围限制（1949年至今）
     * - 金额计算尊重用户输入，仅在金额为空时自动计算
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(CyberAsset asset) {
        log.info("开始新增网信资产，ID：{}", asset.getId());

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

        // 2.5 资产内容校验：必填
        validateAssetContent(asset);

        // 2.6 实有数量校验：必填，非负整数
        validateActualQuantity(asset);

        // 2.7 计量单位校验：必填
        validateUnit(asset);

        // 2.8 单价校验：可选，如果填写则必须非负
        validateUnitPrice(asset);

        // 2.9 投入使用日期校验：必填，≥1949-10-01且≤当前日期
        validatePutIntoUseDate(asset);

        // 2.10 已用数量校验：必填，非负整数且≤实有数量
        validateUsedQuantity(asset);

        // 2.11 盘点单位校验：必填
        validateInventoryUnit(asset);

        // ==================== 3. 数据处理阶段 ====================

        // 3.1 系统自动生成创建时间
        asset.setCreateTime(LocalDateTime.now());

        // 3.2 计算金额（如果金额为空，且有单价和数量，则自动计算）
        calculateAmount(asset);

        // ==================== 4. 数据保存阶段 ====================

        baseMapper.insert(asset);
        log.info("新增网信资产成功，ID：{}，资产名称：{}", asset.getId(), asset.getAssetName());

        // ==================== 5. 上报单位表同步阶段 ====================

        // 5.1 上报单位表同步（单条新增场景）
        // 使用填充后的省市信息同步上报单位表，设置网信资产状态标志为1
        provinceAutoFillTool.syncReportUnit(
                asset.getReportUnit(),  // 上报单位名称
                asset.getProvince(),    // 网信资产有省份字段，使用填充后的省份
                "cyber",                // 资产类型：网信
                false                   // isDelete=false：新增场景
        );
        log.debug("网信资产新增完成，已同步上报单位表状态");
    }

// ==================== 详细的校验方法 ====================

    /**
     * 2.1 主键校验
     * 规则：必填，唯一标识，数字字母组合，确保在组内唯一且不与之前组别冲突
     */
    private void validatePrimaryKey(CyberAsset asset) {
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
    private void validateReportUnit(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getReportUnit())) {
            throw new RuntimeException("上报单位不能为空");
        }
        log.debug("上报单位：{}", asset.getReportUnit());
    }

    /**
     * 2.3 分类编码与资产分类校验
     * 规则：必填，与资产分类严格匹配，使用CategoryMapUtils中的网信表映射
     */
    private void validateCategory(CyberAsset asset) {
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
    private void validateAssetName(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getAssetName())) {
            throw new RuntimeException("资产名称不能为空");
        }
        log.debug("资产名称：{}", asset.getAssetName());
    }

    /**
     * 2.5 资产内容校验
     * 规则：必填，直接写出资产内容的信息，如IP地址范围段、频谱范围段、手机号码区间段等
     */
    private void validateAssetContent(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getAssetContent())) {
            throw new RuntimeException("资产内容不能为空");
        }

        // 检查资产内容是否包含区间格式（如电话号码区间）
        if (asset.getAssetCategory().contains("电话") || asset.getAssetCategory().contains("号码")) {
            if (!asset.getAssetContent().matches(".*\\[.*,.*\\].*")) {
                log.warn("电话号码类资产内容建议使用区间格式，如[0451-83210000, 0451-83213999]");
            }
        }

        log.debug("资产内容：{}", asset.getAssetContent());
    }

    /**
     * 2.6 实有数量校验
     * 规则：必填，非负整数
     */
    private void validateActualQuantity(CyberAsset asset) {
        if (asset.getActualQuantity() == null) {
            throw new RuntimeException("实有数量不能为空");
        }

        if (asset.getActualQuantity() < 0) {
            throw new RuntimeException("实有数量必须为非负整数");
        }

        log.debug("实有数量：{}", asset.getActualQuantity());
    }

    /**
     * 2.7 计量单位校验
     * 规则：必填，如"个"、"芯"、"条"等，无固定选项
     */
    private void validateUnit(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getUnit())) {
            throw new RuntimeException("计量单位不能为空");
        }
        log.debug("计量单位：{}", asset.getUnit());
    }

    /**
     * 2.8 单价校验
     * 规则：可选，如果填写则必须非负
     */
    private void validateUnitPrice(CyberAsset asset) {
        // 可选字段，如果有值则校验非负
        if (asset.getUnitPrice() != null) {
            if (asset.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("单价不能为负数");
            }
            log.debug("单价：{}", asset.getUnitPrice());
        }
    }

    /**
     * 2.9 投入使用日期校验
     * 规则：必填，日期格式（YYYY-MM-DD）从1949年到现在
     */
    private void validatePutIntoUseDate(CyberAsset asset) {
        if (asset.getPutIntoUseDate() == null) {
            throw new RuntimeException("投入使用日期不能为空");
        }

        LocalDate minDate = LocalDate.of(1949, 10, 1);
        LocalDate maxDate = LocalDate.now();

        if (asset.getPutIntoUseDate().isBefore(minDate)) {
            throw new RuntimeException("投入使用日期不能早于1949年10月1日");
        }

        if (asset.getPutIntoUseDate().isAfter(maxDate)) {
            throw new RuntimeException("投入使用日期不能晚于当前日期");
        }

        log.debug("投入使用日期：{}", asset.getPutIntoUseDate());
    }

    /**
     * 2.10 已用数量校验
     * 规则：必填，非负整数，且≤实有数量
     */
    private void validateUsedQuantity(CyberAsset asset) {
        if (asset.getUsedQuantity() == null) {
            throw new RuntimeException("已用数量不能为空");
        }

        if (asset.getUsedQuantity() < 0) {
            throw new RuntimeException("已用数量必须为非负整数");
        }

        if (asset.getUsedQuantity() > asset.getActualQuantity()) {
            throw new RuntimeException("已用数量不能大于实有数量");
        }

        log.debug("已用数量：{}", asset.getUsedQuantity());
    }

    /**
     * 2.11 盘点单位校验
     * 规则：必填
     */
    private void validateInventoryUnit(CyberAsset asset) {
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
    private void calculateAmount(CyberAsset asset) {
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
     * 修改网信基础资产（集成上报单位表同步 + 更新创建时间）
     * 功能概述：
     * 本方法用于修改单条网信资产记录，包含数据校验、业务处理、数据更新和上报单位表同步功能。
     * 核心特点：修改成功后，将创建时间更新为当前时间，作为最后修改时间的参考。

     * 核心流程：
     * 1. 数据存在性校验阶段 → 2. 自动填充省市阶段 → 3. 数据校验阶段 → 4. 数据处理阶段 → 5. 数据更新阶段 → 6. 上报单位表同步阶段

     * 数据校验规则（与新增一致）：
     * 3.1 主键校验：必填，确保存在
     * 3.2 上报单位校验：必填
     * 3.3 分类编码与资产分类校验：必填，严格匹配
     * 3.4 资产名称校验：必填
     * 3.5 资产内容校验：必填
     * 3.6 实有数量校验：必填，非负整数
     * 3.7 计量单位校验：必填
     * 3.8 单价校验：可选，如果填写则必须非负
     * 3.9 投入使用日期校验：必填，≥1949-10-01且≤当前日期
     * 3.10 已用数量校验：必填，非负整数且≤实有数量
     * 3.11 盘点单位校验：必填

     * 特殊处理逻辑：
     * - 创建时间更新：修改成功后，将创建时间更新为当前时间
     * - 省市自动填充：根据上报单位名称自动推导省市信息
     * - 上报单位变更：需要同步新旧两个单位的状态
     * - 已用数量约束：必须≤实有数量

     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或更新失败都会回滚整个事务
     *
     * @param asset 网信资产对象（包含修改后的数据）
     * @throws RuntimeException 当资产不存在、数据校验失败或更新失败时抛出业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CyberAsset asset) {
        log.info("🔄 开始修改网信资产，ID：{}", asset.getId());

        // ==================== 1. 数据存在性校验阶段 ====================

        if (!StringUtils.hasText(asset.getId())) {
            throw new RuntimeException("修改网信资产失败：主键ID不能为空");
        }

        CyberAsset existingAsset = baseMapper.selectById(asset.getId());
        if (existingAsset == null) {
            throw new RuntimeException("修改网信资产失败：资产不存在，ID：" + asset.getId());
        }

        String originalReportUnit = existingAsset.getReportUnit();
        String newReportUnit = asset.getReportUnit();
        boolean reportUnitChanged = !Objects.equals(originalReportUnit, newReportUnit);

        log.debug("📋 找到原网信资产记录 - ID: {}, 原上报单位: {}, 新上报单位: {}",
                asset.getId(), originalReportUnit, newReportUnit);

        // ==================== 2. 自动填充省市阶段 ====================

        // 2.1 自动填充省市信息（更新模式，尊重用户输入但可以自动修正）
        provinceAutoFillTool.fillAssetProvinceCity(asset, true);
        log.debug("🌍 省市自动填充完成 - 省份：{}，城市：{}", asset.getProvince(), asset.getCity());

        // ==================== 3. 数据校验阶段 ====================

        // 注意：主键校验已在第一步完成，此处不再重复校验
        validateReportUnit(asset);              // 3.1 上报单位校验
        validateCategory(asset);                // 3.2 分类编码与资产分类校验
        validateAssetName(asset);               // 3.3 资产名称校验
        validateAssetContent(asset);            // 3.4 资产内容校验
        validateActualQuantity(asset);          // 3.5 实有数量校验
        validateUnit(asset);                    // 3.6 计量单位校验
        validateUnitPrice(asset);               // 3.7 单价校验
        validatePutIntoUseDate(asset);          // 3.8 投入使用日期校验
        validateUsedQuantity(asset);            // 3.9 已用数量校验
        validateInventoryUnit(asset);           // 3.10 盘点单位校验

        log.debug("✅ 网信资产数据校验通过，ID：{}", asset.getId());

        // ==================== 4. 数据处理阶段 ====================

        // 4.1 重新计算金额
        calculateAmount(asset);

        // ==================== 5. 数据更新阶段 ====================

        // 5.1 在更新前设置创建时间为当前时间（作为最后修改时间）
        asset.setCreateTime(LocalDateTime.now());

        // 5.2 执行更新操作
        int updateCount = baseMapper.updateById(asset);
        if (updateCount == 0) {
            throw new RuntimeException("修改网信资产失败，ID：" + asset.getId());
        }

        log.info("✅ 修改网信资产成功，ID：{}，资产名称：{}，创建时间已更新",
                asset.getId(), asset.getAssetName());

        // ==================== 6. 上报单位表同步阶段 ====================

        // 6.1 根据上报单位是否变更，决定同步策略
        if (!reportUnitChanged) {
            // 上报单位未变更，只同步当前单位
            provinceAutoFillTool.syncReportUnit(
                    newReportUnit,      // 上报单位名称
                    asset.getProvince(), // 使用填充后的省份信息
                    "cyber",            // 资产类型：网信
                    false               // isDelete=false：更新场景
            );
            log.debug("🔄 网信资产修改完成（单位未变更），已同步上报单位表状态 - 单位: {}, 省份: {}",
                    newReportUnit, asset.getProvince());
        } else {
            // 上报单位变更，同步新旧两个单位
            provinceAutoFillTool.syncReportUnit(
                    originalReportUnit, // 原上报单位名称
                    existingAsset.getProvince(), // 原省份信息
                    "cyber",            // 资产类型：网信
                    true                // isDelete=true：原单位可能不再有此资产
            );

            provinceAutoFillTool.syncReportUnit(
                    newReportUnit,      // 新上报单位名称
                    asset.getProvince(), // 新省份信息
                    "cyber",            // 资产类型：网信
                    false               // isDelete=false：新单位有此资产
            );

            log.debug("🔄 网信资产修改完成（单位已变更），已同步新旧单位状态");
        }
    }

    /**
     * 删除网信基础资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于删除单条网信资产记录，包含资产存在性校验、数据删除和上报单位表同步功能。
     * 网信资产表与其他资产表的主要区别：有省市字段，需要同时维护自身字段和上报单位表。

     * 核心流程：
     * 1. 资产存在性校验阶段 → 2. 数据删除阶段 → 3. 上报单位表同步阶段

     * 业务规则：
     * - 必须先查询资产是否存在，获取完整的资产信息（包括省市）
     * - 删除操作必须同步更新上报单位表的状态标志
     * - 使用事务确保数据一致性，任何步骤失败都会回滚

     * 同步逻辑：
     * - 调用 provinceAutoFillTool.syncReportUnit 方法
     * - 设置 isDelete=true，表示删除场景
     * - 如果该单位不再有网信资产，系统会自动将网信资产状态标志设为0
     * - 使用资产中的省份信息进行同步，确保数据准确性

     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或删除失败都会回滚整个事务
     * - rollbackFor = Exception.class 确保所有异常都会触发回滚

     * 适用场景：
     * - 前端手动删除网信资产
     * - 需要完整事务管理和上报单位同步的业务场景
     * - 单条记录删除操作

     * 注意事项：
     * - 删除前必须查询资产信息，获取上报单位名称和省市信息用于同步
     * - 删除后需要同步上报单位表，确保状态标志准确
     * - 如果资产不存在，抛出明确的业务异常信息
     * - 网信资产有省市字段，同步时需要传递省份参数
     *
     * @param id 网信资产主键ID，必填参数
     * @throws RuntimeException 当资产不存在或删除失败时抛出业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(String id) {
        log.info("🚀 开始删除网信资产，ID：{}", id);

        // ==================== 1. 资产存在性校验阶段 ====================

        // 1.1 根据ID查询资产信息（包含省市字段）
        CyberAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            log.error("❌ 网信资产不存在，删除失败，ID：{}", id);
            throw new RuntimeException("网信资产不存在，ID：" + id);
        }

        // 1.2 获取上报单位和省市信息，用于后续同步操作
        String reportUnit = asset.getReportUnit();
        String province = asset.getProvince();
        log.debug("📋 找到待删除网信资产 - ID: {}, 上报单位: {}, 省份: {}, 资产名称: {}",
                id, reportUnit, province, asset.getAssetName());

        // ==================== 2. 数据删除阶段 ====================

        // 2.1 执行物理删除操作
        int deleteCount = baseMapper.deleteById(id);
        if (deleteCount == 0) {
            log.error("❌ 网信资产删除失败，可能已被其他操作删除，ID：{}", id);
            throw new RuntimeException("删除网信资产失败，ID：" + id);
        }

        log.info("✅ 删除网信资产成功，ID：{}，资产名称：{}", id, asset.getAssetName());

        // ==================== 3. 上报单位表同步阶段 ====================

        // 3.1 同步上报单位表状态（删除场景）
        // 作用：更新上报单位表中该单位的网信资产状态标志
        // 逻辑：如果该单位不再有网信资产，系统会自动将cyber_asset_status设为0
        provinceAutoFillTool.syncReportUnit(
                reportUnit,           // 上报单位名称（从已删除资产获取）
                province,             // 网信资产有省份字段，使用资产中的省份信息
                "cyber",              // 资产类型：网信资产
                true                  // isDelete=true：删除场景，触发状态标志更新
        );
        log.debug("🔄 网信资产删除完成，已同步上报单位表状态 - 单位: {}, 省份: {}", reportUnit, province);
    }

    @Override
    public boolean checkCategoryMatch(String categoryCode, String assetCategory) {
        String legalCategory = CATEGORY_MAP.get(categoryCode);
        if (legalCategory == null) {
            log.warn("网信资产分类编码非法：{}（无对应标准分类）", categoryCode);
            return false;
        }
        return legalCategory.trim().equals(assetCategory.trim());
    }

    @Override
    public void validateUsedQuantity(Integer usedQuantity, Integer actualQuantity) {
        if (usedQuantity == null) {
            throw new RuntimeException("网信资产已用数量不能为空");
        }
        if (actualQuantity == null) {
            throw new RuntimeException("网信资产实有数量不能为空");
        }
        if (usedQuantity < 0) {
            throw new RuntimeException("已用数量不能为负数（当前值：" + usedQuantity + "）");
        }
        if (actualQuantity < 0) {
            throw new RuntimeException("实有数量不能为负数（当前值：" + actualQuantity + "）");
        }
        if (usedQuantity > actualQuantity) {
            throw new RuntimeException("已用数量超限！已用：" + usedQuantity + "，实有：" + actualQuantity);
        }
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

    // ============================ 新增方法实现（接口方法） ============================

    @Override
    public void addCyberAsset(CyberAsset asset) {
        // 直接调用原有的 add 方法，因为 add 方法已经集成了省市自动填充和上报单位同步
        add(asset);
        log.debug("通过 addCyberAsset 方法新增网信资产成功，ID：{}", asset.getId());
    }

    @Override
    public void updateCyberAsset(CyberAsset asset) {
        // 直接调用原有的 update 方法，因为 update 方法已经集成了省市自动填充和上报单位同步
        update(asset);
        log.debug("通过 updateCyberAsset 方法修改网信资产成功，ID：{}", asset.getId());
    }

    @Override
    public void deleteCyberAsset(String id) {
        // 直接调用原有的 remove 方法，因为 remove 方法已经集成了上报单位同步
        remove(id);
        log.debug("通过 deleteCyberAsset 方法删除网信资产成功，ID：{}", id);
    }

    // ============================ 新增额外接口 ============================
    /**
     * 接口1
     * 统计网信资产数量
     */
    @Override
    public long count() {
        // 使用MyBatis-Plus的count方法
        return this.getBaseMapper().selectCount(null);
    }

    /**
     * 接口2
     * 返回快速查询结果
     */
    @Override
    public Page<CyberAsset> queryByCategory(Page<CyberAsset> page, String categoryCode, String assetCategory) {
        return this.getBaseMapper().queryByCategory(page, categoryCode, assetCategory);
    }

    /**
     * 接口3
     * 实现按上报单位查询网信资产
     * 调用Mapper层的queryByReportUnit方法执行SQL查询
     */
    @Override
    public Page<CyberAsset> queryByReportUnit(Page<CyberAsset> page, String reportUnit) {
        return this.getBaseMapper().queryByReportUnit(page, reportUnit);
    }

// ==================== 新增：接口4相关方法实现 ====================

    @Override
    public List<Map<String, Object>> getProvinceUnitStats() {
        /**
         * 实现网信资产表省份单位统计（新逻辑：关联report_unit表）
         * 设计考虑：虽然cyber_asset表有province列，但为了保持一致性
         * 统一通过关联report_unit表获取省份信息

         * SQL执行逻辑：
         *   SELECT ru.province, COUNT(DISTINCT ca.report_unit) as count
         *   FROM cyber_asset ca
         *   INNER JOIN report_unit ru ON ca.report_unit = ru.report_unit
         *   WHERE ru.province IS NOT NULL AND ru.province != ''
         *   GROUP BY ru.province
         *   ORDER BY count DESC

         * 优势：
         * - 统一了三个资产表的省份数据来源
         * - 避免使用可能为空或不准确的资产表province字段
         * - 确保统计结果的可比性
         */
        return this.getBaseMapper().selectProvinceUnitStats();
    }

// ============================ 🆕 新增方法（清空再导入专用） ============================

    /**
     * 清空网信资产表并重置上报单位表状态（导入专用）
     * 🎯 核心操作：
     * 1. 清空cyber_asset表的所有数据
     * 2. 将report_unit表中source_table_cyber_asset字段全部设为0

     * 💡 重要说明：
     * - 只重置网信资产状态，不影响其他资产表的状态
     * - 不清空report_unit表的其他字段（省市信息等）
     * - 使用事务确保数据一致性

     * 🚨 风险提示：
     * - 此操作会永久删除所有网信资产数据
     * - 只能在导入前调用，确保数据备份
     *
     * @throws RuntimeException 当清空操作失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCyberTableAndResetStatus() {
        log.info("🗑️ 开始清空网信资产表并重置上报单位表状态...");

        try {
            // 1. 清空cyber_asset表的所有数据
            int deletedCount = baseMapper.delete(null); // 删除所有记录
            log.info("✅ 清空网信资产表完成，共删除{}条记录", deletedCount);

            // 2. 重置report_unit表中网信资产状态为0
            int updatedCount = baseMapper.resetCyberAssetStatus();
            log.info("✅ 重置上报单位表网信资产状态完成，共更新{}条记录", updatedCount);

            log.info("🎉 网信资产表和状态重置完成");
        } catch (Exception e) {
            log.error("❌ 清空网信资产表失败: {}", e.getMessage(), e);
            throw new RuntimeException("清空网信资产表失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存网信资产并同步省市信息（导入专用）
     * 🎯 与普通批量保存的区别：
     * 1. 批量处理省市信息（Excel有值优先，无值则推导）
     * 2. 批量同步上报单位表状态
     * 3. 不检查数据重复（因为表已清空）

     * 💡 网信资产特殊处理：
     * - 网信资产表有省市字段，需要特殊处理
     * - 检查Excel中的省市信息：
     *   - 如果Excel有省市：使用Excel的值
     *   - 如果Excel无省市：根据单位名称批量推导
     * - 批量更新上报单位表的省市字段和网信状态标志

     * 🔧 性能优化：
     * - 按单位名称分组，相同单位只推导一次
     * - 批量更新上报单位表，减少数据库操作
     * - 使用事务确保数据一致性
     *
     * @param assets 校验通过的网信资产列表
     * @throws RuntimeException 当批量保存失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveForImport(List<CyberAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            log.info("ℹ️ 批量保存网信资产：无数据需要保存");
            return;
        }

        log.info("💾 开始批量保存网信资产并同步省市信息，共{}条数据", assets.size());

        try {
            // 1. 批量处理省市信息（Excel有值优先，无值则推导）
            processProvinceCityForBatch(assets);

            // 2. 批量保存到cyber_asset表
            boolean saveResult = saveBatch(assets);
            if (!saveResult) {
                throw new RuntimeException("批量保存网信资产失败");
            }
            log.info("✅ 批量保存网信资产成功，共{}条", assets.size());

            // 3. 按上报单位分组，用于批量同步
            Map<String, List<CyberAsset>> unitGroupedAssets = assets.stream()
                    .collect(Collectors.groupingBy(CyberAsset::getReportUnit));

            log.info("📊 按单位分组完成，共{}个不同单位", unitGroupedAssets.size());

            // 4. 批量同步上报单位表（网信资产特殊处理：有省市字段）
            List<ProvinceAutoFillTool.UnitSyncRequest> syncRequests = new ArrayList<>();
            for (Map.Entry<String, List<CyberAsset>> entry : unitGroupedAssets.entrySet()) {
                String unitName = entry.getKey();
                // 获取该单位的第一个资产的省市信息（相同单位的省市应该一致）
                CyberAsset firstAsset = entry.getValue().get(0);
                syncRequests.add(new ProvinceAutoFillTool.UnitSyncRequest(
                        unitName,
                        firstAsset.getProvince(),  // 使用网信资产的省市信息
                        "cyber",
                        false   // 新增模式
                ));
            }

            // 执行批量同步
            provinceAutoFillTool.batchSyncReportUnits(syncRequests);

            log.info("✅ 网信资产批量导入完成，省市信息同步完成，涉及{}个单位", unitGroupedAssets.size());

        } catch (Exception e) {
            log.error("❌ 批量保存网信资产失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量保存网信资产失败: " + e.getMessage());
        }
    }

    /**
     * 批量处理省市信息（网信资产）- 最简版本
     * 🎯 移除场景统计，专注于核心功能
     */
    private void processProvinceCityForBatch(List<CyberAsset> assets) {
        log.info("🔄 开始批量处理网信资产省市信息，共{}条数据", assets.size());

        for (CyberAsset asset : assets) {
            // 直接调用自动填充逻辑
            provinceAutoFillTool.fillAssetProvinceCity(asset, false);
        }

        log.info("✅ 批量处理网信资产省市信息完成");
    }

    /**
     * 网信资产导出查询方法实现
     * 作用：根据前端传递的动态条件查询网信资产数据，用于导出功能
     * 特点：
     * - 支持任意条件组合，所有参数都是可选的
     * - 不分页查询，返回所有匹配的数据
     * - 复用现有的联合查询逻辑，确保查询条件一致性
     * - 包含完整的日志记录，便于问题排查和系统监控

     * 参数说明：
     * @param reportUnit 上报单位（可选）- 按单位筛选
     * @param province 省份（可选）- 按省份筛选
     * @param city 城市（可选）- 按城市筛选
     * @param categoryCode 分类编码（可选）- 按分类编码筛选
     * @param assetCategory 资产分类（可选）- 按资产分类筛选
     * @param quantityMin 实有数量最小值（可选）- 数量范围查询
     * @param quantityMax 实有数量最大值（可选）- 数量范围查询
     * @param usedQuantityMin 已用数量最小值（可选）- 已用数量范围查询
     * @param usedQuantityMax 已用数量最大值（可选）- 已用数量范围查询
     * @param startUseDateStart 投入使用日期开始（可选）- 日期范围查询
     * @param startUseDateEnd 投入使用日期结束（可选）- 日期范围查询
     * @param inventoryUnit 盘点单位（可选）- 按盘点单位筛选
     *
     * @return List<CyberAsset> 返回所有匹配的网信资产数据列表
     * 技术实现：
     * - 使用超大分页(1, Integer.MAX_VALUE)获取所有数据
     * - 复用combinedQuery方法，避免重复代码
     * - 动态条件处理由combinedQuery内部实现
     * - 完整的异常处理和日志记录
     */
    /**
     * 网信资产联合查询方法实现
     * 作用：根据动态条件分页查询网信资产数据
     * 注意：使用Java原生字符串判断，避免额外依赖
     */
    @Override
    public Page<CyberAsset> combinedQuery(Page<CyberAsset> pageInfo,
                                          String reportUnit, String province, String city,
                                          String categoryCode, String assetCategory, Integer quantityMin,
                                          Integer quantityMax, Integer usedQuantityMin, Integer usedQuantityMax,
                                          String startUseDateStart, String startUseDateEnd, String inventoryUnit) {
        try {
            log.info("执行网信资产联合查询 - 条件: reportUnit={}, province={}, city={}",
                    reportUnit, province, city);

            // 构建查询条件
            QueryWrapper<CyberAsset> queryWrapper = new QueryWrapper<>();

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
            if (categoryCode != null && !categoryCode.trim().isEmpty()) {
                queryWrapper.like("category_code", categoryCode);
            }
            if (assetCategory != null && !assetCategory.trim().isEmpty()) {
                queryWrapper.like("asset_category", assetCategory);
            }
            if (quantityMin != null) {
                queryWrapper.ge("actual_quantity", quantityMin);
            }
            if (quantityMax != null) {
                queryWrapper.le("actual_quantity", quantityMax);
            }
            if (usedQuantityMin != null) {
                queryWrapper.ge("used_quantity", usedQuantityMin);
            }
            if (usedQuantityMax != null) {
                queryWrapper.le("used_quantity", usedQuantityMax);
            }
            if (startUseDateStart != null && !startUseDateStart.trim().isEmpty()) {
                queryWrapper.ge("put_into_use_date", startUseDateStart);
            }
            if (startUseDateEnd != null && !startUseDateEnd.trim().isEmpty()) {
                queryWrapper.le("put_into_use_date", startUseDateEnd);
            }
            if (inventoryUnit != null && !inventoryUnit.trim().isEmpty()) {
                queryWrapper.like("inventory_unit", inventoryUnit);
            }

            // 执行分页查询
            Page<CyberAsset> result = baseMapper.selectPage(pageInfo, queryWrapper);
            log.info("网信资产联合查询完成，共{}条数据", result.getRecords().size());

            return result;

        } catch (Exception e) {
            log.error("网信资产联合查询失败", e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    // ============================== 新增：各省份资产统计方法 ==============================
    /**
     * 获取各省份网信资产统计概览
     * 作用：统计34个省份+"未知"的网信资产数量和占比
     *
     * 核心逻辑：
     * 1. 直接使用网信资产表的province字段进行统计
     * 2. 统计每个省份的网信资产数量
     * 3. 计算每个省份网信资产占总量的百分比
     * 4. 包含"未知"省份的统计
     *
     * 技术特点：
     * - 网信资产表有独立的province字段，无需关联查询
     * - 使用COALESCE处理null值，确保统计完整性
     * - 支持"未知"省份的准确统计
     *
     * @return 包含总数量和各省份统计的结果
     */
    @Override
    public Map<String, Object> getProvinceAssetOverview() {
        log.info("开始统计各省份网信资产数量和占比...");

        Map<String, Object> result = new HashMap<>();

        // 1. 获取网信资产总数
        long totalCyberCount = baseMapper.selectCount(null);
        result.put("totalCyberCount", totalCyberCount);

        // 2. 获取各省份网信资产统计
        List<Map<String, Object>> provinceStats = baseMapper.selectProvinceCyberStats();

        // 3. 转换为前端需要的格式并计算百分比
        List<Map<String, Object>> formattedStats = new ArrayList<>();
        for (Map<String, Object> stat : provinceStats) {
            String province = (String) stat.get("province");
            Long count = (Long) stat.get("count");

            Map<String, Object> formattedStat = new HashMap<>();
            formattedStat.put("province", province != null ? province : "未知");  // 修改：将"其他"改为"未知"
            formattedStat.put("cyberCount", count);

            // 计算百分比
            double percentage = totalCyberCount > 0 ?
                    (count.doubleValue() / totalCyberCount) * 100 : 0.0;
            formattedStat.put("cyberPercentage", Math.round(percentage * 10.0) / 10.0);

            formattedStats.add(formattedStat);
        }

        result.put("cyberProvinceStats", formattedStats);
        log.info("网信资产省份统计完成 - 总数: {}, 省份数量: {}", totalCyberCount, formattedStats.size());

        return result;
    }

    /**
     * 获取指定省份网信资产的资产分类细分
     * 作用：统计指定省份下各网信资产分类的数量和占比，确保返回完整的固定分类列表
     *
     * 核心业务逻辑：
     * 1. 查询该省份网信资产总数
     * 2. 查询该省份各分类的实际统计数据
     * 3. 初始化所有网信资产固定分类映射表
     * 4. 创建包含所有固定分类的统计结果，默认数量为0
     * 5. 用实际查询结果更新对应分类的数量
     * 6. 计算各分类在该省份中的占比
     * 7. 返回完整的分类细分统计结果
     *
     * 技术特点：
     * - 使用LinkedHashMap保持17个网信资产分类的顺序一致
     * - 网信资产表有独立的province字段，无需关联查询
     * - 确保返回所有固定分类，包括"其他网信基础资产"分类
     * - 基于该省份网信资产总数计算百分比
     *
     * @param province 省份名称
     * @return 包含分类细分的统计结果
     */
    @Override
    public Map<String, Object> getProvinceAssetCategoryDetail(String province) {
        log.info("开始统计省份网信资产分类细分 - 省份: {}", province);

        Map<String, Object> result = new HashMap<>();
        result.put("province", province);
        result.put("assetType", "cyber");

        // 1. 获取该省份网信资产总数
        Long provinceTotalCount = baseMapper.selectCyberCountByProvince(province);
        if (provinceTotalCount == null) provinceTotalCount = 0L;
        result.put("totalCount", provinceTotalCount);
        log.debug("省份网信资产总数统计完成 - 省份: {}, 总数: {}", province, provinceTotalCount);

        // 2. 获取该省份各资产分类的实际统计数据
        List<Map<String, Object>> categoryStats = baseMapper.selectCyberCategoryStatsByProvince(province);
        log.debug("获取到{}条网信资产分类统计记录", categoryStats.size());

        // 3. 定义所有网信资产分类的固定列表（使用分类名称作为标识）
        List<String> allCategoryNames = Arrays.asList(
                "自动电话号码", "人工电话号码", "保密电话号码", "移动手机号码",
                "有线信道", "光缆纤芯", "骨干网节点互联网络地址", "骨干网节点设备管理地址",
                "网络地址", "文电名录", "军事网络域名", "互联网域名",
                "无线电报代号", "电磁频谱", "数据中心计算资产", "数据中心存储资产", "其他网信基础资产"
        );

        // 4. 创建分类统计映射，初始化所有分类数量为0
        Map<String, Long> categoryCountMap = new LinkedHashMap<>(); // 使用LinkedHashMap保持顺序
        for (String categoryName : allCategoryNames) {
            categoryCountMap.put(categoryName, 0L);
        }
        log.debug("初始化了{}个网信资产分类", categoryCountMap.size());

        // 5. 填充实际统计数据
        for (Map<String, Object> stat : categoryStats) {
            String categoryName = (String) stat.get("asset_category"); // 直接获取分类名称
            Long count = (Long) stat.get("count");

            log.debug("处理网信资产分类统计 - 分类名称: {}, 数量: {}", categoryName, count);

            if (categoryName != null && categoryCountMap.containsKey(categoryName)) {
                categoryCountMap.put(categoryName, count);
                log.debug("成功更新网信资产分类统计 - 分类: {}, 数量: {}", categoryName, count);
            } else {
                log.warn("未知的网信资产分类名称: {}，已忽略", categoryName);
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
        log.info("网信资产分类细分统计完成 - 省份: {}, 总数: {}, 分类数: {}",
                province, provinceTotalCount, formattedStats.size());

        return result;
    }
}
