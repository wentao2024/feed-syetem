package com.feedsystem.user.repository;

import com.feedsystem.user.entity.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    Page<Follow> findByFolloweeId(Long followeeId, Pageable pageable);

    Page<Follow> findByFollowerId(Long followerId, Pageable pageable);

    long countByFolloweeId(Long followeeId);

    long countByFollowerId(Long followerId);

    // Used by Feed Service fan-out: get all follower IDs for a user
    @Query("SELECT f.followerId FROM Follow f WHERE f.followeeId = :followeeId")
    List<Long> findFollowerIdsByFolloweeId(Long followeeId);
}
