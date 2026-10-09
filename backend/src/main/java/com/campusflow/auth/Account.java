package com.campusflow.auth;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@TableName("sys_user")
public class Account {
    @TableId(type = IdType.AUTO) public Long id;
    public String username;
    @JsonIgnore public String passwordHash;
    public String role;
    public Boolean enabled;
    public LocalDateTime createdAt;
    public Long version;
}
