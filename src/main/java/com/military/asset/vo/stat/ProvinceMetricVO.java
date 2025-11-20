package com.military.asset.vo.stat;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 省份指标返回对象。
 *
 * <p>用于前端展示某项指标在各省份的数值，例如信息化程度、国产化率。</p>
 */
public class ProvinceMetricVO {

    /** 省份编码 */
    private String code;

    /** 省份名称 */
    private String name;

    /** 指标数值 */
    private BigDecimal value;

    public ProvinceMetricVO() {
    }

    public ProvinceMetricVO(String code, String name, BigDecimal value) {
        this.code = code;
        this.name = name;
        this.value = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProvinceMetricVO that = (ProvinceMetricVO) o;
        return Objects.equals(code, that.code) &&
                Objects.equals(name, that.name) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name, value);
    }

    @Override
    public String toString() {
        return "ProvinceMetricVO{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
