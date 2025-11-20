package com.military.asset.mapper;

import com.military.asset.entity.Province;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProvinceMapper {
    // 根据省份名称查询省份（用于直接匹配“省”）
    Province selectByName(String name);

    // 根据ProvinceId查询省份（用于通过市/区县反向匹配）
    Province selectById(Integer provinceId);

    // 查询所有省份名称（用于构建内存匹配库）
    List<String> selectAllProvinceNames();

    // 新增：查询所有省份
    List<Province> selectAll();
}
