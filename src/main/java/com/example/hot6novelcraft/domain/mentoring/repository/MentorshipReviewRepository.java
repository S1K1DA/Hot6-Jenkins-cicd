package com.example.hot6novelcraft.domain.mentoring.repository;

import com.example.hot6novelcraft.domain.mentoring.entity.MentorshipReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentorshipReviewRepository extends JpaRepository<MentorshipReview, Long> {

    @Query("SELECT AVG(r.rating) FROM MentorshipReview r WHERE r.mentorshipId IN " +
            "(SELECT m.id FROM Mentorship m WHERE m.mentorId = :mentorId)")
    Double findAverageRatingByMentorId(@Param("mentorId") Long mentorId);

    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentorId = :mentorId AND m.status = 'COMPLETED'")
    long countCompletedSessionsByMentorId(@Param("mentorId") Long mentorId);

    @Query("SELECT COUNT(DISTINCT m.menteeId) FROM Mentorship m WHERE m.mentorId = :mentorId")
    long countTotalMenteesByMentorId(@Param("mentorId") Long mentorId);
}
