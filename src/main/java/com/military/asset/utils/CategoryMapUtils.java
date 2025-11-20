// 1. package声明：指定工具类属于utils包，对应项目结构中com.military.asset.utils文件夹
// 作用：将通用工具类集中存放，与listener/vo/entity包区分，方便管理和调用
package com.military.asset.utils;

// 2. import导入：引入需要的Java自带类
// import java.util.HashMap;：引入HashMap类，用于存储“分类编码→资产分类”的键值对（无序存储，查询高效）
import java.util.HashMap;
// import java.util.Map;：引入Map接口，HashMap是Map的实现类，定义键值对存储规范
import java.util.Map;

/**
 * 三表分类映射工具类（后续开发完成后启用，当前未集成到监听器）
 * 作用：为监听器提供“分类编码-资产分类”合法对应关系，匹配数据库表category_code/asset_category约束
 * 修改说明：后续调整分类时，直接修改initXXXCategoryMap()中的Map即可
 */
public class CategoryMapUtils {
    /**
     * 1. 软件应用资产表：分类编码→资产分类（匹配数据库software_asset表约束）
     * @return 软件表分类映射Map
     *
     * 006004002001001
     * 006004002001002
     * 006004002001003
     * 006004002001004
     * 006004002002001
     * 006004002002002
     * 006004002002003
     * 006004002002004
     * 006004002002005
     * 006004002002006
     * 006004002002007
     * 006004002002008
     * 006004002002009
     * 006004002003001
     * 006004002003002
     * 006004002003003
     * 操作系统
     * 数据库系统
     * 中间件
     * 软件开发环境
     * 网络通信软件
     * 文档处理软件
     * 图形图像软件
     * 数据处理软件
     * 模型算法软件
     * 地理信息系统
     * 移动应用软件
     * 安全防护软件
     * 设备管理软件
     * 作战指挥软件
     * 业务管理软件
     * 日常办公软件
     *
     */
    public static Map<String, String> initSoftwareCategoryMap() {
        Map<String, String> softwareCategoryMap = new HashMap<>();
        // 示例对应关系（后续可按实际需求修改）
        softwareCategoryMap.put("006004002001001", "操作系统");
        softwareCategoryMap.put("006004002001002", "数据库系统");
        softwareCategoryMap.put("006004002001003", "中间件");
        softwareCategoryMap.put("006004002001004", "软件开发环境");
        softwareCategoryMap.put("006004002002001", "网络通信软件");
        softwareCategoryMap.put("006004002002002", "文档处理软件");
        softwareCategoryMap.put("006004002002003", "图形图像软件");
        softwareCategoryMap.put("006004002002004", "数据处理软件");
        softwareCategoryMap.put("006004002002005", "模型算法软件");
        softwareCategoryMap.put("006004002002006", "地理信息系统");
        softwareCategoryMap.put("006004002002007", "移动应用软件");
        softwareCategoryMap.put("006004002002008", "安全防护软件");
        softwareCategoryMap.put("006004002002009", "设备管理软件");
        softwareCategoryMap.put("006004002003001", "作战指挥软件");
        softwareCategoryMap.put("006004002003002", "业务管理软件");
        softwareCategoryMap.put("006004002003003", "日常办公软件");
        return softwareCategoryMap;
    }

    /**
     * 2. 网信基础资产表：分类编码→资产分类（匹配数据库cyber_asset表约束）
     * @return 网信表分类映射Map
     *
     * 006004001001	自动电话号码
     * 006004001002	人工电话号码
     * 006004001003	保密电话号码
     * 006004001004	移动手机号码
     * 006004001005	有线信道
     * 006004001006	光缆纤芯
     * 006004001007	骨干网节点互联网络地址
     * 006004001008	骨干网节点设备管理地址
     * 006004001009	网络地址
     * 006004001010	文电名录
     * 006004001011	军事网络域名
     * 006004001012	互联网域名
     * 006004001014	无线电报代号
     * 006004001015	电子频谱
     * 006004001016	数据中心计算资产
     * 006004001017	数据中心存储资产
     * 006004001999	其他网信基础资产
     */
    public static Map<String, String> initCyberCategoryMap() {
        Map<String, String> cyberCategoryMap = new HashMap<>();
        // 示例对应关系（后续可按实际需求修改）
        cyberCategoryMap.put("006004001001", "自动电话号码");
        cyberCategoryMap.put("006004001002", "人工电话号码");
        cyberCategoryMap.put("006004001003", "保密电话号码");
        cyberCategoryMap.put("006004001004", "移动手机号码");
        cyberCategoryMap.put("006004001005", "有线信道");
        cyberCategoryMap.put("006004001006", "光缆纤芯");
        cyberCategoryMap.put("006004001007", "骨干网节点互联网络地址");
        cyberCategoryMap.put("006004001008", "骨干网节点设备管理地址");
        cyberCategoryMap.put("006004001009", "网络地址");
        cyberCategoryMap.put("006004001010", "文电名录");
        cyberCategoryMap.put("006004001011", "军事网络域名");
        cyberCategoryMap.put("006004001012", "互联网域名");
        cyberCategoryMap.put("006004001014", "无线电报代号");
        cyberCategoryMap.put("006004001015", "电磁频谱");
        cyberCategoryMap.put("006004001016", "数据中心计算资产");
        cyberCategoryMap.put("006004001017", "数据中心存储资产");
        cyberCategoryMap.put("006004001999", "其他网信基础资产");
        return cyberCategoryMap;
    }

    /**
     * 3. 数据内容资产表：分类编码→资产分类（匹配数据库data_content_asset表约束）
     * @return 数据表分类映射Map
     */
    public static Map<String, String> initDataCategoryMap() {
        Map<String, String> dataCategoryMap = new HashMap<>();
        // 示例对应关系（后续可按实际需求修改）
        dataCategoryMap.put("006004003", "数据内容资产");
        return dataCategoryMap;
    }
}