package com.github.api.sample.entity;

import java.util.Date;

/**
 * 员工实体类
 *
 * @author echils
 * @since 2021-06-12 15:43:15
 */
public class Employee {

    /**
     * 唯一标识
     */
    private String id;

    /**
     * 员工姓名
     */
    private String name;

    /**
     * 员工年龄
     */
    private int age;

    /**
     * 所属部门信息
     */
    private Department department;

    /**
     * 员工工作邮箱
     */
    private String email;

    /**
     * 入职时间
     */
    private Date entryTime;

    /**
     * 工作状态
     */
    private WorkState workState;


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

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Date getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(Date entryTime) {
        this.entryTime = entryTime;
    }

    public WorkState getWorkState() {
        return workState;
    }

    public void setWorkState(WorkState workState) {
        this.workState = workState;
    }
}
