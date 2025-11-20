package com.military.asset.entity;

/**
 * 统一3个资产表的规则：必须包含上报单位、省、市的get/set方法
 * 作用：让填充工具类统一处理3个资产表，无需重复代码
 */
public interface HasReportUnitAndProvince {

    /**
     * 获取上报单位名称（推导省市的依据）
     * @return 上报单位名称
     */
    String getReportUnit();

    /**
     * 获取省份信息
     * @return 省份名称
     */
    String getProvince();

    /**
     * 设置省份（自动填充/Excel值）
     * @param province 省份名称
     */
    void setProvince(String province);

    /**
     * 获取城市信息
     * @return 城市名称
     */
    String getCity();

    /**
     * 设置城市（自动填充/Excel值/首府补全）
     * @param city 城市名称
     */
    void setCity(String city);
}
