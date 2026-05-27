package com.feedsystem.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedsystem.user.entity.Follow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {

    @Select("SELECT follower_id FROM follows WHERE followee_id = #{followeeId}")
    List<Long> selectFollowerIdsByFolloweeId(@Param("followeeId") Long followeeId);

    @Select("SELECT f.followee_id FROM follows f " +
            "INNER JOIN (SELECT followee_id FROM follows GROUP BY followee_id HAVING COUNT(*) >= #{threshold}) lv " +
            "ON f.followee_id = lv.followee_id " +
            "WHERE f.follower_id = #{followerId}")
    List<Long> selectLargeVFolloweeIds(@Param("followerId") Long followerId,
                                        @Param("threshold") int threshold);
}
