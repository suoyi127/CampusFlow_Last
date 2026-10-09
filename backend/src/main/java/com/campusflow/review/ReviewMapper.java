package com.campusflow.review;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReviewMapper extends BaseMapper<SpaceReview> {
    @Select("SELECT * FROM space_review WHERE user_id=#{userId} AND space_id=#{spaceId} FOR UPDATE")
    SpaceReview lockOwned(long userId,long spaceId);
    @Select("SELECT * FROM space_review WHERE id=#{id} FOR UPDATE") SpaceReview lock(long id);
}
