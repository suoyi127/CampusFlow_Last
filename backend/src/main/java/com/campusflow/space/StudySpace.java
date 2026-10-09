package com.campusflow.space;

import com.baomidou.mybatisplus.annotation.*;

@TableName("study_space")
public class StudySpace {
    @TableId(type = IdType.AUTO) public Long id;
    public String name;
    public String type;
    public String address;
    public Double latitude;
    public Double longitude;
    public Integer capacity;
    public String openTime;
    public String closeTime;
    public String openDays;
    public Boolean allDay;
    public String facilities;
    public String description;
    public Boolean enabled;
}
