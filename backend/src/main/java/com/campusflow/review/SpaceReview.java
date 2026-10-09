package com.campusflow.review;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("space_review")
public class SpaceReview {
    @TableId(type=IdType.AUTO) public Long id;
    public Long userId;
    public Long spaceId;
    public Integer environmentScore;
    public Integer facilityScore;
    public String content;
    public String status;
    public Long reviewedBy;
    public LocalDateTime reviewedAt;
    public String reviewReason;
    public LocalDateTime updatedAt;
    public Long version;
}
