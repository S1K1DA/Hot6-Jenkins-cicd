package com.example.hot6novelcraft.domain.reviewai.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.domain.reviewai.client.AiReviewClient;
import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final EpisodeRepository episodeRepository;
    private final NovelRepository novelRepository;
    private final AiReviewClient aiReviewClient;

    // AI 리뷰 기능
    @Transactional(readOnly = true)
    public AiReviewResponse getReview(Long episodeId, UserDetailsImpl userDetails) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 회차 조회
        Episode episode = findEpisodeById(episodeId);

        // 본인 소설 확인
        validateOwnership(episode.getNovelId(), userDetails.getUser().getId());

        // 발행 전(DRAFT) 회차만 AI 리뷰 가능
        if (episode.getStatus() != EpisodeStatus.DRAFT) {
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_ONLY_DRAFT);
        }

        // 본문 비어있으면 거부
        String content = episode.getContent();
        if (content == null || content.isBlank()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_CONTENT_EMPTY);
        }

        // OpenAI 호출 (매번 새로)
        log.info("[AI 리뷰 신규 생성] episodeId={}", episodeId);
        try {
            return aiReviewClient.generate(episodeId, episode.getTitle(), content);
        } catch (RuntimeException e) {
            log.error("[AI 리뷰 호출/파싱 실패] episodeId={}", episodeId, e);
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_GENERATION_FAILED);
        }
    }

    // ----------------------------- 공통 메서드 -----------------------------

    private void validateAuthorRole(UserDetailsImpl userDetails) {
        if (userDetails.getUser().getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }
    }

    private Episode findEpisodeById(Long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));

        if (episode.isDeleted()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_DELETED);
        }
        return episode;
    }

    private void validateOwnership(Long novelId, Long userId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 본인 소설 확인 (AI 리뷰 전용 메시지)
        if (!novel.getAuthorId().equals(userId)) {
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_FORBIDDEN);
        }
    }
}