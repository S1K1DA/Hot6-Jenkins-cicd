package com.example.hot6novelcraft.domain.mentoring.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentorshipCreateRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorshipCreateResponse;
import com.example.hot6novelcraft.domain.mentoring.service.MentorshipService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/mentorships")
@RequiredArgsConstructor
public class MentorshipController {

    private final MentorshipService mentorshipService;

    /**
     * 멘토링 신청
     * 정은식
     */
    @PostMapping
    public ResponseEntity<BaseResponse<MentorshipCreateResponse>> applyMentorship(
            @Valid @RequestBody MentorshipCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long menteeId = userDetails.getUser().getId();

        MentorshipCreateResponse response = mentorshipService.applyMentorship(menteeId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "멘토링 신청이 완료되었습니다.", response));
    }
}