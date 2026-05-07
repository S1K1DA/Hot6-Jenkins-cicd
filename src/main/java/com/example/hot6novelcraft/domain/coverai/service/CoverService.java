package com.example.hot6novelcraft.domain.coverai.service;

import com.example.hot6novelcraft.common.exception.domain.CoverExceptionEnum;
import com.example.hot6novelcraft.domain.coverai.dto.event.CoverGenerationEvent;
import com.example.hot6novelcraft.domain.coverai.dto.response.CoverJobResponse;
import com.example.hot6novelcraft.domain.coverai.entity.CoverJob;
import com.example.hot6novelcraft.domain.coverai.producer.CoverGenerationProducer;
import com.example.hot6novelcraft.domain.coverai.repository.CoverJobRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoverService {

    private final UserRepository userRepository;
    private final NovelRepository novelRepository;
    private final CoverJobRepository coverJobRepository;
    private final CoverGenerationProducer coverGenerationProducer;

    @Transactional
    public CoverJobResponse generateCover(Long novelId, Long userId) {

        // 1. 작가 권한 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> CoverExceptionEnum.USER_NOT_FOUND.toException());

        if (user.getRole() != UserRole.AUTHOR) {
            throw CoverExceptionEnum.NOT_AUTHOR.toException();
        }

        // 2. 소설 조회 및 본인 소설 확인
        Novel novel = novelRepository.findByIdAndIsDeletedFalse(novelId)
                .orElseThrow(() -> CoverExceptionEnum.NOVEL_NOT_FOUND.toException());

        if (!novel.getAuthorId().equals(userId)) {
            throw CoverExceptionEnum.NOT_NOVEL_OWNER.toException();
        }

        // 3. Job 생성 (포인트 차감은 Consumer에서 처리)
        String jobId = UUID.randomUUID().toString();
        CoverJob job = CoverJob.create(jobId, novelId, userId);
        coverJobRepository.save(job);

        // 4. Kafka 발행 → 즉시 반환
        coverGenerationProducer.publish(new CoverGenerationEvent(jobId, novelId, userId));

        log.info("[Cover] 표지 생성 요청 완료 jobId={} novelId={}", jobId, novelId);
        return CoverJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public CoverJobResponse getJobStatus(String jobId, Long userId) {
        CoverJob job = coverJobRepository.findByJobId(jobId)
                .orElseThrow(() -> CoverExceptionEnum.JOB_NOT_FOUND.toException());

        if (!job.getUserId().equals(userId)) {
            throw CoverExceptionEnum.NOT_NOVEL_OWNER.toException();
        }

        return CoverJobResponse.from(job);
    }
}