package com.github.api.sample.controller;

import com.github.api.sample.entity.Employee;
import com.github.api.sample.entity.WorkState;
import com.github.api.sample.entity.wrap.ResultData;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * 员工控制器
 *
 * @author echils
 */
@RestController
public class EmployeeController {


    /**
     * 查询所有员工
     *
     * @param names         员工姓名
     * @param departmentIds 部门唯一标识列表
     * @param state         员工状态
     */
    @GetMapping("/employees")
    public ResultData<List<Employee>> findAllEmployees(@RequestParam(required = false) String names,
                                                       @RequestParam(required = false) List<String> departmentIds,
                                                       @RequestParam(required = false, defaultValue = "NORMAL") WorkState state) {

        //忽略业务代码，关注返回实体类
        return ResultData.success(Collections.singletonList(new Employee()));
    }

    /**
     * 查询员工信息
     *
     * @param id 员工唯一标识
     */
    @GetMapping("/employee/{id}")
    public ResultData<Employee> findById(@PathVariable String id) {

        //忽略业务代码，关注返回实体类
        return ResultData.success(new Employee());
    }

    /**
     * 新增员工
     *
     * @param employee 员工信息
     */
    @PostMapping("/employee")
    public ResultData<Employee> insert(@RequestBody Employee employee) {

        //忽略业务代码，关注返回实体类
        return ResultData.insertSuccess(new Employee());
    }


    /**
     * 批量导入员工
     *
     * @param file 员工导入文件
     */
    @PostMapping("/employee_upload")
    public ResultData upload(@RequestBody MultipartFile file) {

        //忽略业务代码，关注返回实体类
        return ResultData.insertSuccess(null);
    }


    /**
     * 更新员工信息
     *
     * @param id       员工唯一标识
     * @param employee 员工信息
     */
    @PutMapping("/employee/{id}")
    public ResultData<Employee> update(@PathVariable String id,
                                       @RequestBody Employee employee) {

        //忽略业务代码，关注返回实体类
        return ResultData.updateSuccess(new Employee());
    }


    /**
     * 删除员工
     *
     * @param id 员工唯一标识
     */
    @DeleteMapping("/employee/{id}")
    public ResultData delete(@PathVariable String id) {

        //忽略业务代码，关注返回实体类
        return ResultData.deleteSuccess();
    }

}
