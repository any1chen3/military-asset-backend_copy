package com.military.asset.vo;

import lombok.Data;

/**
 * 三表CRUD统一返回体
 * 格式：{code:200, message:"操作成功", data:...}
 * 避免IDE报错：泛型支持所有数据类型，方法重载适配有/无数据场景
 */
@Data
public class ResultVO<T> {
    // 状态码：200=成功，500=失败
    private Integer code;
    // 提示消息
    private String message;
    // 业务数据（列表、实体、空）
    private T data;

    /**
     * 成功返回（带数据+自定义消息）
     */
    public static <T> ResultVO<T> success(T data, String message) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(200);
        vo.setMessage(message);
        vo.setData(data);
        return vo;
    }

    /**
     * 成功返回（无数据+默认消息）
     */
    public static ResultVO<Void> success(String message) {
        ResultVO<Void> vo = new ResultVO<>();
        vo.setCode(200);
        vo.setMessage(message);
        vo.setData(null);
        return vo;
    }

    /**
     * 失败返回（带错误消息）
     */
    public static <T> ResultVO<T> fail(String message) {
        ResultVO<T> vo = new ResultVO<>();
        vo.setCode(500);
        vo.setMessage(message);
        vo.setData(null);
        return vo;
    }
}