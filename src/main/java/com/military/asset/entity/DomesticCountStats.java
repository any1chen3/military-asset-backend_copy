package com.military.asset.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DomesticCountStats {
    private int total;
    private BigDecimal average = BigDecimal.ZERO;
    private BigDecimal median = BigDecimal.ZERO;
    private BigDecimal variance = BigDecimal.ZERO;
}
