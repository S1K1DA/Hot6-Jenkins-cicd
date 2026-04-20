package com.example.hot6novelcraft.domain.mentor.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;

public record MentorStatusResponse(
        Long mentorId,
        MentorStatus status,
        String rejectReason
) {
    public static MentorStatusResponse from(Mentor mentor) {
        return new MentorStatusResponse(
                mentor.getId(),
                mentor.getStatus(),
                mentor.getRejectReason()
        );
    }
}