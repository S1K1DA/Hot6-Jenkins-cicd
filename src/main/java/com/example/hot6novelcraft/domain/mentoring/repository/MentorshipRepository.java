package com.example.hot6novelcraft.domain.mentoring.repository;

import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MentorshipRepository extends JpaRepository<Mentorship, Long> {

    Page<Mentorship> findAllByMentorIdOrderByCreatedAtDesc(Long mentorId, Pageable pageable);

    // 대기 중 건수
    long countByMentorIdAndStatus(Long mentorId, MentorshipStatus status);

    // 이번 달 수락 건수 - acceptedAt 기준으로 집계 (COMPLETED로 변경되어도 누락 없음)
    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentorId = :mentorId AND m.acceptedAt >= :startOfMonth")
    long countAcceptedThisMonth(@Param("mentorId") Long mentorId,
                                @Param("startOfMonth") LocalDateTime startOfMonth);

    // 이번 달 거절 건수 - rejectedAt 기준으로 집계
    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentorId = :mentorId AND m.rejectedAt >= :startOfMonth")
    long countRejectedThisMonth(@Param("mentorId") Long mentorId,
                                @Param("startOfMonth") LocalDateTime startOfMonth);

    List<Mentorship> findAllByMentorIdAndStatus(Long mentorId, MentorshipStatus status);

    // 멘티가 이미 PENDING,ACCEPTED 멘토링 있는지 확인 (1:1 제약)
    boolean existsByMenteeIdAndStatusIn(Long menteeId, List<MentorshipStatus> statuses);

}