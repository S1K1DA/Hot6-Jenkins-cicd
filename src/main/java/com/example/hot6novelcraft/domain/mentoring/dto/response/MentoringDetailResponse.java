package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MentoringDetailResponse(
        Long mentoringId,
        String title,
        String mentorName,
        String menteeName,
        MentorshipStatus status,
        LocalDateTime startDate,
        int totalSessions,
        List<FeedbackInfo> feedbacks
) {
    public record FeedbackInfo(
            Long feedbackId,
            String content,
            LocalDateTime createdAt
    ) {
        public static FeedbackInfo from(MentorFeedback feedback) {
            return new FeedbackInfo(
                    feedback.getId(),
                    feedback.getContent(),
                    feedback.getCreatedAt()
            );
        }
    }

    public static MentoringDetailResponse of(Mentorship mentorship, String mentorName,
                                             String menteeName, List<FeedbackInfo> feedbacks) {
        return new MentoringDetailResponse(
                mentorship.getId(),
                mentorship.getTitle(),
                mentorName,
                menteeName,
                mentorship.getStatus(),
                mentorship.getAcceptedAt(),
                mentorship.getTotalSessions(),
                feedbacks
        );
    }
}
