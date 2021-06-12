package com.github.api.sample.controller;

import com.github.api.sample.entity.Department;
import com.github.api.sample.entity.wrap.ResultData;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * 部门控制器
 *
 * @author echils
 * @since 2021-06-12 15:41:58
 */
@RestController
public class DepartmentController {


    /**
     * 查询所有部门
     *
     * @param name      部门名称
     * @param showStaff 是否展示部门员工
     */
    @GetMapping("/departments")
    public ResultData<List<Department>> findAllEmployees(@RequestParam(value = "name", required = false) String name,
                                                         @RequestParam(value = "showStaff", required = false, defaultValue = "false") boolean showStaff) {

        //忽略业务代码，关注返回实体类
        return ResultData.success(Collections.singletonList(new Department()));
    }

    /**
     * 查询部门信息
     *
     * @param id 部门唯一标识
     */
    @GetMapping("/department/{id}")
    public ResultData<Department> findById(@PathVariable(value = "id") String id) {

        //忽略业务代码，关注返回实体类
        return ResultData.success(new Department());
    }

    /**
     * 新增部门
     *
     * @param department 部门信息
     */
    @PostMapping("/department")
    public ResultData<Department> insert(@RequestBody Department department) {

        //忽略业务代码，关注返回实体类
        return ResultData.insertSuccess(new Department());
    }


    /**
     * 批量导入部门
     *
     * @param file 部门导入文件
     */
    @PostMapping("/department_upload")
    public ResultData upload(@RequestBody MultipartFile file) {

        //忽略业务代码，关注返回实体类
        return ResultData.insertSuccess(null);
    }


    /**
     * 更新部门信息
     *
     * @param id         部门唯一标识
     * @param department 部门信息
     */
    @PutMapping("/department/{id}")
    public ResultData<Department> update(@PathVariable(value = "id") String id,
                                         @RequestBody Department department) {

        //忽略业务代码，关注返回实体类
        return ResultData.updateSuccess(new Department());
    }


    /**
     * 删除部门
     *
     * @param id 部门唯一标识
     */
    @DeleteMapping("/department/{id}")
    public ResultData delete(@PathVariable(value = "id") String id) {

        //忽略业务代码，关注返回实体类
        return ResultData.deleteSuccess();
    }


}
