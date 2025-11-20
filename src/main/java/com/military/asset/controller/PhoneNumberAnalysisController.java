package com.military.asset.controller;

import com.military.asset.service.PhoneNumberAnalysisService;
import com.military.asset.vo.PhoneNumberAnalysisVO;
import com.military.asset.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电话号码分类分析接口。
 */
@RestController
@RequestMapping("/api/asset/cyber/phone-number")
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberAnalysisController {

    private final PhoneNumberAnalysisService analysisService;

    @GetMapping("/report-unit/{reportUnit}")
    public ResultVO<PhoneNumberAnalysisVO> analyze(@PathVariable String reportUnit) {
        try {
            PhoneNumberAnalysisVO vo = analysisService.analyze(reportUnit);
            return ResultVO.success(vo, "电话资产分类分析成功");
        } catch (Exception ex) {
            log.error("电话资产分类分析失败, reportUnit={}", reportUnit, ex);
            return ResultVO.fail("电话资产分类分析失败：" + ex.getMessage());
        }
    }
}
