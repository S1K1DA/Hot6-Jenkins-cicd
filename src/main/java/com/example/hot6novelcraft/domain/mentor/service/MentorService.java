package com.example.hot6novelcraft.domain.mentor.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorRegisterRequest;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorUpdateRequest;
import com.example.hot6novelcraft.domain.mentor.dto.response.MentorProfileResponse;
import com.example.hot6novelcraft.domain.mentor.dto.response.MentorRegisterResponse;
import com.example.hot6novelcraft.domain.mentor.dto.response.MentorUpdateResponse;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorService {

    private static final long INTRODUCTION_MIN_EPISODES = 50L;
    private static final long ELEMENTARY_MIN_EPISODES = 50L;
    private static final long ELEMENTARY_MIN_LIKES = 50L;
    private static final long INTERMEDIATE_MIN_EPISODES = 100L;
    private static final long INTERMEDIATE_MIN_LIKES = 100L;

    private final MentorRepository mentorRepository;
    private final NovelRepository novelRepository;
    private final EpisodeRepository episodeRepository;
    private final ObjectMapper objectMapper;

    // 멘토 등록 신청
    // PENDING 또는 APPROVED 상태의 기존 신청이 있으면 중복 신청 불가
    // certificationFile 은 S3 업로드 후 URL 저장 (현재는 파일명으로 대체)
    @Transactional
    public MentorRegisterResponse register(Long userId, MentorRegisterRequest request,
                                           MultipartFile certificationFile) {
        if (mentorRepository.existsByUserIdAndStatus(userId, MentorStatus.PENDING)) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_PENDING_EXISTS);
        }
        if (mentorRepository.existsByUserIdAndStatus(userId, MentorStatus.APPROVED)) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_ALREADY_APPROVED);
        }

        String fileUrl = uploadCertificationFile(certificationFile);
        MentorStatus initialStatus = resolveInitialStatus(userId, request.careerLevel());

        Mentor mentor = Mentor.create(
                userId,
                request.careerLevel(),
                toJson(request.mainGenres()),
                toJson(request.specialFields()),
                toJson(request.mentoringStyles()),
                request.bio(),
                request.awardsCareer(),
                request.maxMentees(),
                request.allowInstant(),
                request.preferredMenteeDesc(),
                fileUrl,
                initialStatus
        );

        Mentor saved = mentorRepository.save(mentor);
        return MentorRegisterResponse.from(saved);
    }

    // 멘토 정보 수정
    // 본인 확인 후 변경 가능한 필드만 업데이트
    @Transactional
    public MentorUpdateResponse update(Long userId, MentorUpdateRequest request) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        if (request.careerHistory() != null && request.careerHistory().isBlank()) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_CAREER_REQUIRED);
        }

        mentor.update(
                request.introduction(),
                toJson(request.mainGenres()),
                toJson(request.specialFields()),
                toJson(request.mentoringStyles()),
                request.careerHistory(),
                request.maxMentees(),
                request.allowInstant(),
                request.preferredMenteeDesc()
        );

        return MentorUpdateResponse.from(mentor.getId(), LocalDateTime.now());
    }

    // 내 멘토 프로필 조회
    @Transactional(readOnly = true)
    public MentorProfileResponse getMyProfile(Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        return MentorProfileResponse.from(mentor);
    }

    // careerLevel 기준 초기 상태 결정
    // PROFICIENT  : PENDING (관리자 수동 승인)
    // INTERMEDIATE : PUBLISHED 에피소드 100회 이상 + likeCount 100 이상이면 APPROVED
    // ELEMENTARY   : PUBLISHED 에피소드 50회 이상 + likeCount 50 이상이면 APPROVED
    // INTRODUCTION : PUBLISHED 에피소드 50회 이상이면 APPROVED
    private MentorStatus resolveInitialStatus(Long userId, CareerLevel careerLevel) {
        if (careerLevel == CareerLevel.PROFICIENT) {
            return MentorStatus.PENDING;
        }

        List<Long> novelIds = novelRepository.findNovelIdsByAuthorId(userId);
        if (novelIds.isEmpty()) {
            return MentorStatus.PENDING;
        }

        long publishedCount = episodeRepository.countByNovelIdInAndStatus(novelIds, EpisodeStatus.PUBLISHED);
        long totalLikes = episodeRepository.sumLikeCountByNovelIdIn(novelIds);

        return switch (careerLevel) {
            case INTRODUCTION -> publishedCount >= INTRODUCTION_MIN_EPISODES
                    ? MentorStatus.APPROVED : MentorStatus.PENDING;
            case ELEMENTARY -> (publishedCount >= ELEMENTARY_MIN_EPISODES && totalLikes >= ELEMENTARY_MIN_LIKES)
                    ? MentorStatus.APPROVED : MentorStatus.PENDING;
            case INTERMEDIATE -> (publishedCount >= INTERMEDIATE_MIN_EPISODES && totalLikes >= INTERMEDIATE_MIN_LIKES)
                    ? MentorStatus.APPROVED : MentorStatus.PENDING;
            default -> MentorStatus.PENDING;
        };
    }

    private String uploadCertificationFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        // TODO: S3 업로드 로직 연동 필요 - 현재는 원본 파일명 반환
        return file.getOriginalFilename();
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_FILE_UPLOAD_FAILED);
        }
    }
}