package com.feedsystem.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedsystem.user.entity.Follow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {

    @Select("SELECT follower_id FROM follows WHERE followee_id = #{followeeId}")
    List<Long> selectFollowerIdsByFolloweeId(@Param("followeeId") Long followeeId);
}
