package com.github.api.sample.entity;

import java.util.Date;
import java.util.List;

/**
 * 部门实体类
 *
 * @author echils
 * @since 2021-06-12 15:43:54
 */
public class Department {


    /**
     * 唯一标识
     */
    private String id;


    /**
     * 部门名称
     */
    private String name;


    /**
     * 部门领导人唯一标识
     */
    private String leaderUserId;


    /**
     * 部门员工列表
     */
    private List<Employee> employees;


    /**
     * 部门成立时间
     */
    private Date establishedTime;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLeaderUserId() {
        return leaderUserId;
    }

    public void setLeaderUserId(String leaderUserId) {
        this.leaderUserId = leaderUserId;
    }

    public List<Employee> getEmployees() {
        return employees;
    }

    public void setEmployees(List<Employee> employees) {
        this.employees = employees;
    }

    public Date getEstablishedTime() {
        return establishedTime;
    }

    public void setEstablishedTime(Date establishedTime) {
        this.establishedTime = establishedTime;
    }
}
