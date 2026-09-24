package com.guo.common;

/**
 * 全局响应码枚举
 * 规范：200=成功，4xx=客户端错误，5xx=服务端错误
 */
public enum ResultCode {
    // 成功
    SUCCESS(200, "操作成功"),
    // 失败
    FAIL(500, "操作失败"),
    // 客户端错误
    PARAM_ERROR(400, "参数错误"),
    USER_NOT_LOGIN(401, "用户未登录"),
    PERMISSION_DENIED(403, "权限不足"),
    RESOURCE_NOT_FOUND(404, "资源不存在"),
    // 服务端错误
    SYSTEM_ERROR(500, "系统异常"),
    DB_ERROR(501, "数据库操作异常"),
    RPC_ERROR(502, "远程调用异常");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}