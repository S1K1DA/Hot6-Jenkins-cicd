package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.domain.file.service.FileUploadService;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentorshipCreateRequest;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MentorshipServiceTest {

    @InjectMocks
    private MentorshipService mentorshipService;

    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private MentorRepository mentorRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileUploadService fileUploadService;

    // 상수
    private static final Long MENTEE_ID = 1L;
    private static final Long MENTOR_USER_ID = 2L;
    private static final Long MENTOR_ENTITY_ID = 5L;
    private static final Long NOVEL_ID = 100L;

    // 픽스처
    private User menteeUser;
    private Mentor mentor;

    @BeforeEach
    void setUp() {
        menteeUser = User.builder()
                .email("mentee@test.com")
                .password("pw")
                .nickname("홍길동")
                .role(UserRole.AUTHOR)
                .build();

        mentor = Mentor.create(
                MENTOR_USER_ID, CareerLevel.INTRODUCTION,
                "[\"판타지\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                "소개글", "수상경력", 3, true, "멘티 설명", null, MentorStatus.APPROVED
        );
        setField(mentor, "id", MENTOR_ENTITY_ID);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 멘토링 신청 ====================
    @Nested
    @DisplayName("멘토링 신청")
    class ApplyMentorshipTest {

        @Test
        @DisplayName("정상 신청 - 저장 호출")
        void applyMentorship_success() {
            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "신청 동기입니다.", NOVEL_ID, "https://s3.amazonaws.com/test.txt"
            );

            // save() 반환값 mock 추가!
            Mentorship savedMentorship = Mentorship.create(
                    MENTOR_USER_ID, MENTEE_ID, NOVEL_ID, "신청 동기입니다.",
                    "https://s3.amazonaws.com/test.txt"
            );
            setField(savedMentorship, "id", 10L);

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.existsByMenteeIdAndStatusIn(eq(MENTEE_ID), any()))
                    .willReturn(false);
            given(mentorshipRepository.save(any(Mentorship.class))).willReturn(savedMentorship);  // ← 추가!

            mentorshipService.applyMentorship(MENTEE_ID, request);

            verify(mentorshipRepository).save(any(Mentorship.class));
        }

        @Test
        @DisplayName("작가 권한 없으면 예외")
        void applyMentorship_not_author_throws() {
            User reader = User.builder()
                    .email("reader@test.com")
                    .password("pw")
                    .nickname("독자")
                    .role(UserRole.READER)
                    .build();

            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(reader));

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_AUTHOR.getMessage());
        }

        @Test
        @DisplayName("본인에게 신청 시 예외")
        void applyMentorship_self_apply_throws() {
            // mentor의 userId를 menteeId와 같게 설정
            Mentor selfMentor = Mentor.create(
                    MENTEE_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 3, true, "설명", null, MentorStatus.APPROVED
            );
            setField(selfMentor, "id", MENTOR_ENTITY_ID);

            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTEE_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTEE_ID)).willReturn(Optional.of(selfMentor));

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_SELF_APPLY.getMessage());
        }

        @Test
        @DisplayName("이미 진행중인 멘토링 있으면 예외")
        void applyMentorship_already_exists_throws() {
            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.existsByMenteeIdAndStatusIn(eq(MENTEE_ID), any()))
                    .willReturn(true);

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_ALREADY_EXISTS.getMessage());
        }

        @Test
        @DisplayName("정원 마감이면 예외")
        void applyMentorship_slot_full_throws() {
            Mentor fullMentor = Mentor.create(
                    MENTOR_USER_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 0, true, "설명", null, MentorStatus.APPROVED
            );
            setField(fullMentor, "id", MENTOR_ENTITY_ID);

            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.of(fullMentor));
            given(mentorshipRepository.existsByMenteeIdAndStatusIn(eq(MENTEE_ID), any()))
                    .willReturn(false);

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_SLOT_FULL.getMessage());
        }

        @Test
        @DisplayName("멘토를 찾을 수 없으면 예외")
        void applyMentorship_mentor_not_found_throws() {
            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }
    }

    // ==================== 원고 업로드 ====================
    @Nested
    @DisplayName("원고 업로드")
    class UploadManuscriptTest {

        @Test
        @DisplayName("정상 업로드 - S3 URL 반환")
        void uploadManuscript_success() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "content".getBytes()
            );
            String expectedUrl = "https://bucket.s3.amazonaws.com/manuscripts/uuid.txt";

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(fileUploadService.uploadManuscript(file)).willReturn(expectedUrl);

            String result = mentorshipService.uploadManuscript(file, MENTEE_ID);

            assertThat(result).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("작가 권한 없으면 예외")
        void uploadManuscript_not_author_throws() {
            User reader = User.builder()
                    .email("reader@test.com")
                    .password("pw")
                    .nickname("독자")
                    .role(UserRole.READER)
                    .build();

            MultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "content".getBytes()
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(reader));

            assertThatThrownBy(() -> mentorshipService.uploadManuscript(file, MENTEE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_AUTHOR.getMessage());
        }

        @Test
        @DisplayName("유저 없으면 예외")
        void uploadManuscript_user_not_found_throws() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "content".getBytes()
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorshipService.uploadManuscript(file, MENTEE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }
    }
}