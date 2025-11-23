package com.military.asset.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

    /**
     * 省市字典缓存工具类

     * ==================== 核心功能概述 ====================
     * 本工具类负责加载和管理全国省市行政区域数据，提供高效的省市查询服务。
     * 系统启动时自动从JSON文件加载数据到内存缓存，支持快速查询和匹配操作。

     * ==================== 数据来源 ====================
     * - 主数据源：classpath:province/provinceData.json
     * - 备份数据：内置默认省市数据（当JSON文件不可用时）
     * - 数据格式：标准JSON格式，包含省份、首府、地级市信息

     * ==================== 缓存结构 ====================
     * 1. 所有省份名称缓存（allProvinceNames）
     *    - 存储所有省份全称
     *    - 按名称长度倒序排序，避免短名称误匹配
     *    - 用途：省份全称匹配和查询

     * 2. 城市到省份映射缓存（cityToProvinceMap）
     *    - Key：城市名称（地级市）
     *    - Value：所属省份名称
     *    - 用途：通过城市名称推导所属省份

     * 3. 所有城市名称缓存（allCityNames）
     *    - 存储所有地级市名称
     *    - 按名称长度倒序排序，避免短名称误匹配
     *    - 用途：城市全称匹配和查询

     * 4. 省份到首府映射缓存（provinceToCapitalMap）
     *    - Key：省份名称
     *    - Value：首府城市名称
     *    - 用途：通过省份名称获取首府城市

     * ==================== 核心特性 ====================
     * 1. 自动加载：系统启动时自动初始化缓存
     * 2. 性能优化：内存缓存，查询操作O(1)时间复杂度
     * 3. 容错处理：JSON文件缺失时使用默认数据
     * 4. 排序优化：名称按长度倒序，确保准确匹配
     * 5. 数据验证：提供缓存状态验证方法

     * ==================== 主要应用场景 ====================
     * 场景1：省市自动填充
     *   - 为ProvinceAutoFillTool提供省市查询服务
     *   - 支持"填省补市"和"填市补省"逻辑

     * 场景2：数据校验
     *   - 验证用户输入的省市名称合法性
     *   - 标准化省市名称格式

     * 场景3：统计分析
     *   - 提供完整的省市列表用于统计报表
     *   - 支持按省份分组的数据分析

     * ==================== 技术实现细节 ====================
     * 1. 初始化时机：使用@PostConstruct注解，确保系统启动时加载
     * 2. 文件读取：使用ClassPathResource读取classpath下的JSON文件
     * 3. JSON解析：使用Jackson库解析JSON数据
     * 4. 排序策略：名称按长度倒序，确保"北京市"优先于"北京"
     * 5. 异常处理：文件读取失败时使用默认数据，确保系统可用性

     * ==================== 使用注意事项 ====================
     * 1. 缓存初始化：确保provinceData.json文件在正确路径
     * 2. 内存占用：省市数据量较小，不会造成内存压力
     * 3. 数据更新：修改JSON文件后需要重启应用生效
     * 4. 默认数据：当主数据源不可用时使用内置默认数据

     * ==================== 扩展性考虑 ====================
     * 1. 数据源扩展：支持从数据库或其他数据源加载
     * 2. 缓存更新：支持热更新缓存数据
     * 3. 行政区划扩展：可扩展支持县级单位数据

     * ==================== 性能指标 ====================
     * - 初始化时间：< 100ms
     * - 查询性能：O(1) 时间复杂度
     * - 内存占用：< 10MB
     * - 并发安全：只读操作，线程安全
     */
@Component
public class AreaCacheTool {

    private static final Logger logger = LoggerFactory.getLogger(AreaCacheTool.class);

    // 缓存1：所有省份名称（按长度倒序，避免短名称误匹配）
    @Getter
    private final List<String> allProvinceNames = new ArrayList<>();

    // 缓存2：城市名→省份名（填市补省时用，如"南京市"→"江苏省"）
    @Getter
    private final Map<String, String> cityToProvinceMap = new HashMap<>();

    // 缓存3：所有城市名称（按长度倒序，避免匹配错误）
    @Getter
    private final List<String> allCityNames = new ArrayList<>();

    // 缓存4：省份名→首府名（填省补市时用，如"浙江省"→"杭州市"）
    private final Map<String, String> provinceToCapitalMap = new HashMap<>();

    // ============ 🆕 1121 新增：县级单位映射缓存 ============
    /**
     * 县级单位到省市映射缓存
     * - Key：县级单位名称（如"涟水县"）
     * - Value：省份-城市组合（如"江苏省-淮安市"）
     */
    @Getter
    private final Map<String, String> countyToProvinceCityMap = new HashMap<>();

    // ============ 🆕 1121 新增：县级映射JSON文件路径常量 ============
    private static final String COUNTY_JSON_PATH = "province/county-mapping.json";

    // ============  1121 新增：验证缓存的方法 ============
    /**
     * 验证缓存是否正常加载
     */
    public void validateCache() {
        System.out.println("=== AreaCacheTool 缓存验证 ===");
//        System.out.println("省份数量: " + allProvinceNames.size());
//        System.out.println("城市数量: " + allCityNames.size());
        System.out.println("城市到省份映射数量: " + cityToProvinceMap.size());
        System.out.println("省份到首府映射数量: " + provinceToCapitalMap.size());

//        // 验证几个关键数据
//        System.out.println("广东省首府: " + getCapitalByProvinceName("广东省"));
//        System.out.println("广州市对应省份: " + cityToProvinceMap.get("广州市"));
//        System.out.println("北京市首府: " + getCapitalByProvinceName("北京市"));
//        System.out.println("北京市对应省份: " + cityToProvinceMap.get("北京市"));
//
//        // 打印前几个省份和城市
//        System.out.println("前5个省份: " + allProvinceNames.subList(0, Math.min(5, allProvinceNames.size())));
//        System.out.println("前5个城市: " + allCityNames.subList(0, Math.min(5, allCityNames.size())));
        System.out.println("=== AreaCacheTool 验证完成 ===");

        // ============ 1121 新增：验证缓存的方法 ============
        /**
         * 验证缓存是否正常加载
         */
        System.out.println("=== AreaCacheTool 缓存验证 ===");
        System.out.println("城市到省份映射数量: " + cityToProvinceMap.size());
        System.out.println("省份到首府映射数量: " + provinceToCapitalMap.size());
        // 🆕 新增：县级映射验证
        System.out.println("县级单位映射数量: " + countyToProvinceCityMap.size());

        // 🆕 新增：县级映射示例验证
        if (!countyToProvinceCityMap.isEmpty()) {
            String sampleCounty = countyToProvinceCityMap.keySet().iterator().next();
            System.out.println("县级映射示例: " + sampleCounty + " → " + countyToProvinceCityMap.get(sampleCounty));
        }
        System.out.println("=== AreaCacheTool 验证完成 ===");

    }
// ============ 新增结束 ============

    private static final String JSON_PATH = "province/provinceData.json";

    /**
     * 系统启动时自动执行：加载JSON数据到所有缓存
     */
    @PostConstruct
    public void initCache() {
        try {
            // 1. 首先检查文件是否存在
            ClassPathResource resource = new ClassPathResource(JSON_PATH);
            System.out.println("=== 检查省市字典文件 ===");
            System.out.println("JSON文件路径: " + JSON_PATH);
            System.out.println("文件是否存在: " + resource.exists());
            System.out.println("文件路径: " + resource.getPath());
            System.out.println("文件描述: " + resource.getDescription());

            if (!resource.exists()) {
                System.out.println("ERROR: 省市字典文件不存在！路径: " + JSON_PATH);
                System.out.println("请检查文件是否在 classpath 的 province/ 目录下");
                initializeDefaultData();
                return;
            }

            // 2. 读取JSON文件内容
            String jsonContent = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            System.out.println("文件内容长度: " + jsonContent.length() + " 字符");

            // 3. 使用Jackson解析JSON为Java对象
            ObjectMapper objectMapper = new ObjectMapper();
            List<ProvinceDTO> provinceList = objectMapper.readValue(
                    jsonContent,
                    new TypeReference<List<ProvinceDTO>>() {}
            );
            System.out.println("解析出省份数量: " + provinceList.size());

            // 4. 填充所有缓存
            for (ProvinceDTO province : provinceList) {
                String provinceName = province.getProvinceName();
                String capitalCity = province.getCapitalCity();

                // 填充省份缓存
                allProvinceNames.add(provinceName);
                // 填充省→首府缓存
                provinceToCapitalMap.put(provinceName, capitalCity);

                // 填充城市缓存与城市→省份映射
                for (CityDTO city : province.getCities()) {
                    String cityName = city.getCityName();
                    allCityNames.add(cityName);
                    cityToProvinceMap.put(cityName, provinceName);
                }
            }

            // 5. 按名称长度倒序排序（长名称优先匹配，避免"北京"匹配"北京市"）
            allProvinceNames.sort((a, b) -> Integer.compare(b.length(), a.length()));
            allCityNames.sort((a, b) -> Integer.compare(b.length(), a.length()));

            // 🆕 新增：加载县级映射数据
            loadCountyMappingData();

            logger.info("省市字典加载成功：{}个省，{}个市，{}个县",
                    allProvinceNames.size(), allCityNames.size(), countyToProvinceCityMap.size());
            System.out.println("省市字典加载成功：" + allProvinceNames.size() + "个省，" +
                    allCityNames.size() + "个市，" + countyToProvinceCityMap.size() + "个县");

        } catch (IOException e) {
            logger.error("读取provinceData.json失败！请检查路径是否正确: {}", e.getMessage());
            System.out.println("ERROR: 读取省市字典文件失败: " + e.getMessage());
            // 初始化默认数据，避免空指针
            initializeDefaultData();
        }
    }

    /**
     * 初始化默认数据（当JSON文件读取失败时使用）
     */
    private void initializeDefaultData() {
        // 添加一些基本的省市数据作为fallback
        String[][] defaultProvinces = {
                {"北京市", "北京市"},
                {"天津市", "天津市"},
                {"上海市", "上海市"},
                {"重庆市", "重庆市"},
                {"广东省", "广州市"},
                {"浙江省", "杭州市"},
                {"江苏省", "南京市"}
        };

        for (String[] provinceData : defaultProvinces) {
            String provinceName = provinceData[0];
            String capitalCity = provinceData[1];

            allProvinceNames.add(provinceName);
            provinceToCapitalMap.put(provinceName, capitalCity);
            cityToProvinceMap.put(capitalCity, provinceName);
            allCityNames.add(capitalCity);
        }

        allProvinceNames.sort((a, b) -> Integer.compare(b.length(), a.length()));
        allCityNames.sort((a, b) -> Integer.compare(b.length(), a.length()));

        // 🆕 新增：在默认数据中也尝试加载县级映射
        loadCountyMappingData();

        logger.warn("使用默认省市数据，共{}个省，{}个县", allProvinceNames.size(), countyToProvinceCityMap.size());
    }

    /**
     * 根据省份名称获取首府城市
     * @param provinceName 省份名称
     * @return 首府城市名称，如果未找到返回空字符串
     */
    public String getCapitalByProvinceName(String provinceName) {
        if (provinceName == null || provinceName.trim().isEmpty()) {
            return "";
        }
        String capital = provinceToCapitalMap.get(provinceName.trim());
        return capital != null ? capital : "";
    }

    /**
     * 临时内部类：对应JSON中的"省份"结构（仅用于解析JSON）
     */
    @Getter
    @Setter
    static class ProvinceDTO {
        private String provinceName; // 对应JSON的provinceName
        private String capitalCity; // 对应JSON的capitalCity（新增）
        private List<CityDTO> cities; // 对应JSON的cities
    }

    /**
     * 临时内部类：对应JSON中的"城市"结构
     */
    @Getter
    @Setter
    static class CityDTO {
        private String cityName; // 对应JSON的cityName
    }

    //================= 添加县级数据加载方法 ===========================
    /**
     * 🆕 新增：加载县级单位映射数据
     * 从 county-mapping.json 文件加载县级单位到省市的映射关系
     *
     * 数据结构说明：
     * {
     *   "countyMapping": {
     *     "江苏省": {
     *       "淮安市": ["涟水县", "洪泽县", ...],
     *       "连云港市": ["东海县", "灌云县", ...]
     *     },
     *     "广东省": {
     *       "清远市": ["佛冈县", "阳山县", ...]
     *     }
     *   }
     * }
     *
     * 处理逻辑：
     * 1. 读取JSON文件并解析
     * 2. 遍历省份→城市→县级单位的三层结构
     * 3. 构建县级单位→"省份-城市"的映射关系
     * 4. 存储到 countyToProvinceCityMap 缓存中
     *
     * 容错处理：
     * - 文件不存在时记录警告但不影响主流程
     * - JSON解析异常时使用空映射表
     */
    private void loadCountyMappingData() {
        try {
            ClassPathResource countyResource = new ClassPathResource(COUNTY_JSON_PATH);

            if (!countyResource.exists()) {
                logger.warn("县级映射文件不存在，跳过加载: {}", COUNTY_JSON_PATH);
                System.out.println("⚠️ 县级映射文件不存在，跳过加载: " + COUNTY_JSON_PATH);
                return;
            }

            // 读取JSON文件内容
            String countyJsonContent = new String(
                    countyResource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> countyData = objectMapper.readValue(
                    countyJsonContent,
                    new TypeReference<Map<String, Object>>() {}
            );

            // 解析县级映射数据
            @SuppressWarnings("unchecked")
            Map<String, Object> countyMapping = (Map<String, Object>) countyData.get("countyMapping");

            if (countyMapping != null) {
                for (Map.Entry<String, Object> provinceEntry : countyMapping.entrySet()) {
                    String provinceName = provinceEntry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, List<String>> cities = (Map<String, List<String>>) provinceEntry.getValue();

                    for (Map.Entry<String, List<String>> cityEntry : cities.entrySet()) {
                        String cityName = cityEntry.getKey();
                        List<String> counties = cityEntry.getValue();

                        for (String county : counties) {
                            String provinceCityKey = provinceName + "-" + cityName;
                            countyToProvinceCityMap.put(county, provinceCityKey);
                            // 🚫 注释掉这行详细的调试日志，避免输出过多信息
                            // logger.debug("加载县级映射: {} → {}", county, provinceCityKey);
                        }
                    }
                }
            }

            logger.info("县级单位映射数据加载成功，共 {} 个县级单位", countyToProvinceCityMap.size());
            System.out.println("✅ 县级单位映射数据加载成功，共 " + countyToProvinceCityMap.size() + " 个县级单位");

        } catch (Exception e) {
            logger.error("加载县级映射数据失败: {}", e.getMessage(), e);
            System.err.println("❌ 加载县级映射数据失败: " + e.getMessage());
            // 不抛出异常，避免影响主流程
        }
    }

    // ==================== 添加县级数据相关的公共方法 ====================
    /**
     * 🆕 新增：根据县级单位名称获取对应的省市组合
     *
     * 功能说明：
     * - 通过县级单位名称（如"涟水县"）查询对应的省份和城市
     * - 返回格式："省份-城市"（如"江苏省-淮安市"）
     *
     * 应用场景：
     * - ProvinceAutoFillTool中的县级单位推导
     * - 基层单位的省市信息自动填充
     *
     * @param countyName 县级单位名称
     * @return 省份-城市组合字符串，如未找到返回null
     */
    public String getProvinceCityByCounty(String countyName) {
        if (countyName == null || countyName.trim().isEmpty()) {
            return null;
        }
        return countyToProvinceCityMap.get(countyName.trim());
    }

    /**
     * 🆕 新增：获取所有县级单位名称列表
     *
     * 功能说明：
     * - 返回所有已加载的县级单位名称
     * - 列表按名称长度倒序排序，确保准确匹配
     *
     * 排序说明：
     * - 长名称优先匹配，避免"北京县"匹配到"北京"
     * - 提高县级单位名称匹配的准确性
     *
     * @return 按长度倒序排序的县级单位名称列表
     */
    public List<String> getAllCountyNames() {
        List<String> countyNames = new ArrayList<>(countyToProvinceCityMap.keySet());
        countyNames.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return countyNames;
    }
}