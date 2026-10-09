package com.campusflow.space;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SpaceMapper extends BaseMapper<StudySpace> {
    @Select("SELECT * FROM study_space WHERE id = #{id} FOR UPDATE") StudySpace lock(long id);
    @Select("SELECT * FROM study_space ORDER BY id FOR UPDATE") List<StudySpace> lockAll();
}
