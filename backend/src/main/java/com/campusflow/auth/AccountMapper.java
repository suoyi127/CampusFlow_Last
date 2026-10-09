package com.campusflow.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {
    @Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE") Account lock(long id);
    @Select("SELECT * FROM sys_user WHERE enabled=TRUE AND role='SERVER_ADMIN' ORDER BY id FOR UPDATE") List<Account> lockEnabledAdministrators();
}
