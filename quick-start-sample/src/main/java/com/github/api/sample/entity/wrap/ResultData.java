package com.github.api.sample.entity.wrap;

import org.springframework.http.HttpStatus;

/**
 * 结果集包装类
 *
 * @author echils
 * @since 2021-06-12 16:03:12
 */
public class ResultData<T> {


    /**
     * 状态码
     */
    private int code;

    /**
     * 结果集
     */
    private T data;

    /**
     * 描述
     */
    private String msg;


    public static <T> ResultData<T> success(T data) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(HttpStatus.OK.value());
        resultData.setData(data);
        return resultData;
    }

    public static <T> ResultData<T> insertSuccess(T data) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(HttpStatus.CREATED.value());
        resultData.setData(data);
        return resultData;
    }


    public static <T> ResultData<T> updateSuccess(T data) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(HttpStatus.CREATED.value());
        resultData.setData(data);
        return resultData;
    }


    public static <T> ResultData<T> deleteSuccess() {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(HttpStatus.NO_CONTENT.value());
        return resultData;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

}
