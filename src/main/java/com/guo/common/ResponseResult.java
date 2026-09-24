package com.guo.common;

import lombok.Data;

/**
 * 全局统一返回结果
 * @param <T> 响应数据类型
 */
@Data
public class ResponseResult<T> {
    /**
     * 响应码（200=成功，其他=失败）
     */
    private int code;

    /**
     * 响应信息（成功/失败描述）
     */
    private String msg;

    /**
     * 响应数据（成功时返回）
     */
    private T data;

    // 私有构造方法，避免外部直接创建
    private ResponseResult() {}

    // 成功响应（无数据）
    public static <T> ResponseResult<T> success() {
        ResponseResult<T> responseResult = new ResponseResult<>();
        responseResult.setCode(ResultCode.SUCCESS.getCode());
        responseResult.setMsg(ResultCode.SUCCESS.getMsg());
        return responseResult;
    }

    // 成功响应（带数据）
    public static <T> ResponseResult<T> success(T data) {
        ResponseResult<T> responseResult = new ResponseResult<>();
        responseResult.setCode(ResultCode.SUCCESS.getCode());
        responseResult.setMsg(ResultCode.SUCCESS.getMsg());
        responseResult.setData(data);
        return responseResult;
    }

    // 成功响应（自定义消息+数据）
    public static <T> ResponseResult<T> success(String msg, T data) {
        ResponseResult<T> responseResult = new ResponseResult<>();
        responseResult.setCode(ResultCode.SUCCESS.getCode());
        responseResult.setMsg(msg);
        responseResult.setData(data);
        return responseResult;
    }

    // 失败响应（使用默认响应码）
    public static <T> ResponseResult<T> fail() {
        ResponseResult<T> responseResult = new ResponseResult<>();
        responseResult.setCode(ResultCode.FAIL.getCode());
        responseResult.setMsg(ResultCode.FAIL.getMsg());
        return responseResult;
    }

    // 失败响应（自定义消息）
    public static <T> ResponseResult<T> fail(String msg) {
        ResponseResult<T> responseResult = new ResponseResult<>();
        responseResult.setCode(ResultCode.FAIL.getCode());
        responseResult.setMsg(msg);
        return responseResult;
    }

    // 失败响应（自定义响应码+消息）
    public static <T> ResponseResult<T> fail(ResultCode resultCode) {
        ResponseResult<T> responseResult = new ResponseResult<>();
        responseResult.setCode(resultCode.getCode());
        responseResult.setMsg(resultCode.getMsg());
        return responseResult;
    }

    // 失败响应（自定义响应码+消息+数据）
    public static <T> ResponseResult<T> fail(ResultCode resultCode, T data) {
        ResponseResult<T> responseResult = new ResponseResult<>();
        responseResult.setCode(resultCode.getCode());
        responseResult.setMsg(resultCode.getMsg());
        responseResult.setData(data);
        return responseResult;
    }


    public static ResponseResult<Object> fail(Integer resultCode, String msg) {
        ResponseResult<Object> responseResult = new ResponseResult<>();
        responseResult.setCode(resultCode);
        responseResult.setMsg(msg);
        return responseResult;
    }
}