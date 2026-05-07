package com.example.hot6novelcraft.domain.coverai.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.coverai.client.GeminiClient;
import com.example.hot6novelcraft.domain.coverai.dto.response.CoverCreateResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverServiceTest {

    @InjectMocks
    private CoverService coverService;

    @Mock private GeminiClient geminiClient;
    @Mock private NovelRepository novelRepository;
    @Mock private S3Client s3Client;
    @Mock private UserRepository userRepository;
    @Mock private PointHistoryRepository pointHistoryRepository;
    @Mock private PointService pointService;

    private User author;
    private User reader;
    private Novel novel;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(coverService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(coverService, "region", "ap-northeast-2");

        author = User.register("author@test.com", "password", "테스트작가", "01012345678", null, UserRole.AUTHOR);
        ReflectionTestUtils.setField(author, "id", 1L);

        reader = User.register("reader@test.com", "password", "테스트독자", "01098765432", null, UserRole.READER);
        ReflectionTestUtils.setField(reader, "id", 2L);

        novel = Novel.builder()
                .authorId(1L)
                .title("달빛 아래 검은 장미")
                .description("어둠의 마법사와 왕국의 기사가 금지된 사랑을 나누는 이야기")
                .genre("판타지")
                .tags("판타지,로맨스")
                .status(NovelStatus.PENDING)
                .viewCount(0L)
                .bookmarkCount(0)
                .build();
        ReflectionTestUtils.setField(novel, "id", 1L);
        ReflectionTestUtils.setField(novel, "isDeleted", false);
    }

    @Test
    @DisplayName("소설 표지 생성 성공")
    void generateCover_success() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(geminiClient.generateImage(any())).willReturn(new byte[]{1, 2, 3});
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(pointHistoryRepository.save(any(PointHistory.class))).willReturn(null);

        // when
        CoverCreateResponse response = coverService.generateCover(1L, 1L);

        // then
        assertThat(response.novelId()).isEqualTo(1L);
        assertThat(response.coverImageUrl()).contains("test-bucket");
        assertThat(response.coverImageUrl()).contains("ap-northeast-2");
        assertThat(response.coverImageUrl()).contains("covers/1/");

        verify(pointService).deduct(1L, 300L);
        verify(pointHistoryRepository).save(any(PointHistory.class));
        verify(geminiClient).generateImage(any());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("존재하지 않는 유저 - USER_NOT_FOUND 예외")
    void generateCover_userNotFound() {
        // given
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(1L, 99L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("작가가 아닌 경우 - NOT_AUTHOR 예외")
    void generateCover_notAuthor() {
        // given
        given(userRepository.findById(2L)).willReturn(Optional.of(reader));

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(1L, 2L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("작가만 사용할 수 있습니다");
    }

    @Test
    @DisplayName("존재하지 않는 소설 - NOVEL_NOT_FOUND 예외")
    void generateCover_novelNotFound() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(novelRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(99L, 1L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("소설을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("본인 소설이 아닌 경우 - NOT_NOVEL_OWNER 예외")
    void generateCover_notNovelOwner() {
        // given
        User anotherAuthor = User.register("other@test.com", "password", "다른작가", "01011112222", null, UserRole.AUTHOR);
        ReflectionTestUtils.setField(anotherAuthor, "id", 3L);

        given(userRepository.findById(3L)).willReturn(Optional.of(anotherAuthor));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(1L, 3L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("본인의 소설만 표지를 생성할 수 있습니다");
    }

    @Test
    @DisplayName("S3 업로드 실패 - IMAGE_UPLOAD_FAILED 예외")
    void generateCover_s3UploadFailed() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(geminiClient.generateImage(any())).willReturn(new byte[]{1, 2, 3});
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(new RuntimeException("S3 연결 실패"));

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(1L, 1L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("이미지 업로드에 실패했습니다");

        // S3 실패 시 포인트 차감이 일어나지 않아야 함
        verify(pointService, never()).deduct(any(), any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("프롬프트에 소설 제목과 작가명이 포함되는지 확인")
    void generateCover_promptContainsTitleAndAuthor() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(geminiClient.generateImage(any())).willReturn(new byte[]{1, 2, 3});
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(pointHistoryRepository.save(any())).willReturn(null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        // when
        coverService.generateCover(1L, 1L);

        // then
        verify(geminiClient).generateImage(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("달빛 아래 검은 장미");
        assertThat(prompt).contains("테스트작가 지음");
        assertThat(prompt).contains("판타지");
    }
}