package com.example.hot6novelcraft.domain.coverai.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.CoverExceptionEnum;
import com.example.hot6novelcraft.domain.coverai.client.GeminiClient;
import com.example.hot6novelcraft.domain.coverai.dto.response.CoverCreateResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoverService {

    private final GeminiClient geminiClient;
    private final NovelRepository novelRepository;
    private final S3Client s3Client;
    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointService pointService;

    private static final Long COVER_COST = 300L;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    public CoverCreateResponse generateCover(Long novelId, Long userId) {

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

        // 3. 외부 API 먼저 호출 (실패 시 포인트 소실 방지)
        String prompt = buildPrompt(novel, user.getNickname());
        byte[] imageBytes = geminiClient.generateImage(prompt);
        String s3Url = uploadToS3(imageBytes, novelId);

        // 4. 성공 후 포인트 차감 및 이력 저장
        pointService.deduct(userId, COVER_COST);
        pointHistoryRepository.save(
                PointHistory.create(userId, novelId, null, COVER_COST, PointHistoryType.AI_COVER, "AI 소설 표지 생성")
        );

        return CoverCreateResponse.of(novelId, s3Url);
    }

    private String uploadToS3(byte[] imageBytes, Long novelId) {
        try {
            String s3Key = "covers/" + novelId + "/" + UUID.randomUUID() + ".png";

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType("image/png")
                    .contentLength((long) imageBytes.length)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));

            String s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);
            log.info("[Cover] S3 업로드 성공: {}", s3Url);
            return s3Url;

        } catch (ServiceErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Cover] S3 업로드 실패", e);
            throw CoverExceptionEnum.IMAGE_UPLOAD_FAILED.toException();
        }
    }

    private String buildPrompt(Novel novel, String authorName) {
        return String.format(
                "Create a professional Korean novel cover image. " +
                        "The title '%s' must be written clearly and legibly at the top in large, stylish Korean typography. " +
                        "Below the title, write the author name '%s 지음' in smaller Korean text. " +
                        "Genre: %s. Story summary: %s. " +
                        "Style: cinematic, high quality book cover art.",
                novel.getTitle(),
                authorName,
                novel.getGenre(),
                novel.getDescription()
        );
    }
}