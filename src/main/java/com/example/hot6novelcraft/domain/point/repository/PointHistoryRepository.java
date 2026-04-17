package com.example.hot6novelcraft.domain.point.repository;

import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    Page<PointHistory> findByUserId(Long userId, Pageable pageable);

    // 회차 구매 여부 확인
    boolean existsByUserIdAndEpisodeIdAndType(Long userId, Long episodeId, PointHistoryType type);
}