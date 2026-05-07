package com.example.hot6novelcraft.domain.reviewai.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;
import com.example.hot6novelcraft.domain.reviewai.service.AiReviewService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/author/episodes")
public class AiReviewController {

    private final AiReviewService aiReviewService;

    /**
     * AI 리뷰 받기
     * - episodeId의 본문을 기반으로 AI가 평점 + 독자 댓글 생성
     * 정은식
     */
    @PostMapping("/{episodeId}/ai-review")
    public ResponseEntity<BaseResponse<AiReviewResponse>> getAiReview(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        AiReviewResponse response = aiReviewService.getReview(episodeId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "AI 리뷰 조회 성공", response)
        );
    }
}