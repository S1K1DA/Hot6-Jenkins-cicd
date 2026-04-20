package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorFeedbackRepository;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentoringFeedbackRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringDetailResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringFeedbackResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringReceivedResponse;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentoringServiceTest {

    @InjectMocks
    private MentoringService mentoringService;

    @Mock
    private MentorFeedbackRepository mentorFeedbackRepository;

    @Mock
    private MentorshipRepository mentorshipRepository;

    @Mock
    private MentorRepository mentorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NovelRepository novelRepository;

    private static final Long MENTOR_ID = 1L;
    private static final Long MENTEE_ID = 501L;
    private static final Long MENTORING_ID = 10L;
    private static final Long NOVEL_ID = 100L;
    private static final Long MENTOR_ENTITY_ID = 5L;

    private Mentorship mentorship;
    private Mentor mentor;
    private User mentee;
    private Novel novel;

    @BeforeEach
    void setUp() {
        mentorship = Mentorship.create(MENTOR_ENTITY_ID, MENTEE_ID, NOVEL_ID, "신청 동기입니다", "https://s3.amazonaws.com/file.pdf");

        try {
            java.lang.reflect.Field idField = Mentorship.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mentorship, MENTORING_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mentor = Mentor.create(
                MENTOR_ID, CareerLevel.INTRODUCTION,
                "[\"판타지\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                "소개글", "수상경력", 3, true, "멘티 설명", null, MentorStatus.APPROVED
        );

        try {
            java.lang.reflect.Field idField = Mentor.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mentor, MENTOR_ENTITY_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mentee = User.builder()
                .email("mentee@test.com")
                .password("password")
                .nickname("홍길동")
                .role(UserRole.AUTHOR)
                .build();

        novel = Novel.createNovel(MENTOR_ID, "자바 백엔드 로드맵", "소설 설명", "판타지", "태그");
    }

    // ===================== getReceivedMentorings 테스트 =====================

    @Nested
    @DisplayName("내 멘토링 접수 목록 조회")
    class GetReceivedMentoringsTest {

        @Test
        @DisplayName("정상 조회 - menteeName, title 정상 반환")
        void getReceivedMentorings_success() {
            // given
            PageImpl<Mentorship> page = new PageImpl<>(List.of(mentorship));
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any())).willReturn(page);
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(mentee));
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));

            // when
            Page<MentoringReceivedResponse> result = mentoringService.getReceivedMentorings(
                    MENTOR_ENTITY_ID, PageRequest.of(0, 10));

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).menteeName()).isEqualTo("홍길동");
            assertThat(result.getContent().get(0).title()).isEqualTo("자바 백엔드 로드맵");
            assertThat(result.getContent().get(0).status()).isEqualTo(MentorshipStatus.PENDING);
        }

        @Test
        @DisplayName("멘티가 탈퇴한 경우 알 수 없는 사용자 반환")
        void getReceivedMentorings_deleted_mentee() {
            // given
            PageImpl<Mentorship> page = new PageImpl<>(List.of(mentorship));
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any())).willReturn(page);
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.empty());
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));

            // when
            Page<MentoringReceivedResponse> result = mentoringService.getReceivedMentorings(
                    MENTOR_ENTITY_ID, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent().get(0).menteeName()).isEqualTo("알 수 없는 사용자");
        }

        @Test
        @DisplayName("소설이 없는 경우 알 수 없는 소설 반환")
        void getReceivedMentorings_deleted_novel() {
            // given
            PageImpl<Mentorship> page = new PageImpl<>(List.of(mentorship));
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any())).willReturn(page);
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(mentee));
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.empty());

            // when
            Page<MentoringReceivedResponse> result = mentoringService.getReceivedMentorings(
                    MENTOR_ENTITY_ID, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent().get(0).title()).isEqualTo("알 수 없는 소설");
        }

        @Test
        @DisplayName("빈 목록 조회")
        void getReceivedMentorings_empty() {
            // given
            PageImpl<Mentorship> emptyPage = new PageImpl<>(List.of());
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any())).willReturn(emptyPage);

            // when
            Page<MentoringReceivedResponse> result = mentoringService.getReceivedMentorings(
                    MENTOR_ENTITY_ID, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ===================== acceptMentee 테스트 =====================

    @Nested
    @DisplayName("멘티 수락")
    class AcceptMenteeTest {

        @Test
        @DisplayName("정상 수락 - 슬롯 차감 및 상태 변경")
        void acceptMentee_success() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ENTITY_ID)).willReturn(Optional.of(mentor));

            // when
            mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(mentorship.getStatus()).isEqualTo(MentorshipStatus.ACCEPTED);
            assertThat(mentor.getMaxMentees()).isEqualTo(2);
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void acceptMentee_mentoring_not_found_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void acceptMentee_unauthorized_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, 999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("멘티 정보 불일치 시 예외 발생")
        void acceptMentee_mentee_not_match_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, 999L, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_MENTEE_NOT_MATCH.getMessage());
        }

        @Test
        @DisplayName("이미 처리된 멘토링이면 예외 발생")
        void acceptMentee_already_processed_throws() {
            // given
            mentorship.approve();
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_ALREADY_PROCESSED.getMessage());
        }

        @Test
        @DisplayName("멘토 정보가 없으면 예외 발생")
        void acceptMentee_mentor_not_found_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ENTITY_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    // ===================== rejectMentee 테스트 =====================

    @Nested
    @DisplayName("멘티 거절")
    class RejectMenteeTest {

        @Test
        @DisplayName("정상 거절 - 상태 변경")
        void rejectMentee_success() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when
            mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(mentorship.getStatus()).isEqualTo(MentorshipStatus.REJECTED);
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void rejectMentee_mentoring_not_found_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void rejectMentee_unauthorized_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, 999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("멘티 정보 불일치 시 예외 발생")
        void rejectMentee_mentee_not_match_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, 999L, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_MENTEE_NOT_MATCH.getMessage());
        }

        @Test
        @DisplayName("이미 처리된 멘토링이면 예외 발생")
        void rejectMentee_already_processed_throws() {
            // given
            mentorship.reject();
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_ALREADY_PROCESSED.getMessage());
        }
    }

    // ===================== getManuscriptDownloadUrl 테스트 =====================

    @Nested
    @DisplayName("원고 다운로드 URL 조회")
    class GetManuscriptDownloadUrlTest {

        @Test
        @DisplayName("정상 조회 - URL 반환")
        void getManuscriptDownloadUrl_success() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when
            String url = mentoringService.getManuscriptDownloadUrl(MENTORING_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(url).isEqualTo("https://s3.amazonaws.com/file.pdf");
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void getManuscriptDownloadUrl_mentoring_not_found_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.getManuscriptDownloadUrl(MENTORING_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void getManuscriptDownloadUrl_unauthorized_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.getManuscriptDownloadUrl(MENTORING_ID, 999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("원고 URL이 없으면 예외 발생")
        void getManuscriptDownloadUrl_manuscript_not_found_throws() {
            // given
            Mentorship noFilesMentorship = Mentorship.create(
                    MENTOR_ENTITY_ID, MENTEE_ID, NOVEL_ID, "신청 동기입니다", null);
            try {
                java.lang.reflect.Field idField = Mentorship.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(noFilesMentorship, MENTORING_ID);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(noFilesMentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.getManuscriptDownloadUrl(MENTORING_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_MANUSCRIPT_NOT_FOUND.getMessage());
        }
    }
    @Nested
    @DisplayName("멘토링 종료")
    class CompleteMentoringTest {

        @Test
        @DisplayName("정상 종료 - 슬롯 반환 및 상태 변경")
        void completeMentoring_success() {
            // given
            mentorship.approve();
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ENTITY_ID)).willReturn(Optional.of(mentor));

            // when
            mentoringService.completeMentoring(MENTORING_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(mentorship.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
            assertThat(mentor.getMaxMentees()).isEqualTo(4);
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void completeMentoring_mentoring_not_found_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void completeMentoring_unauthorized_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, 999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("진행 중이 아닌 멘토링 종료 시 예외 발생")
        void completeMentoring_not_accepted_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_ACCEPTED.getMessage());
        }

        @Test
        @DisplayName("멘토 정보가 없으면 예외 발생")
        void completeMentoring_mentor_not_found_throws() {
            // given
            mentorship.approve();
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ENTITY_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }
    @Nested
    @DisplayName("멘토링 상세 정보 조회")
    class GetMentoringDetailTest {

        @Test
        @DisplayName("정상 조회 - 멘토링 상세 정보 반환")
        void getMentoringDetail_success() {
            // given
            MentorFeedback feedback = MentorFeedback.create(MENTORING_ID, MENTOR_ID, "ERD 설계 및 API 명세 작성");
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(MENTOR_ENTITY_ID)).willReturn(Optional.of(
                    User.builder().email("mentor@test.com").password("password")
                            .nickname("김철수").role(UserRole.AUTHOR).build()
            ));
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(mentee));
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of(feedback));

            // when
            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.mentorName()).isEqualTo("김철수");
            assertThat(response.menteeName()).isEqualTo("홍길동");
            assertThat(response.status()).isEqualTo(MentorshipStatus.PENDING);
            assertThat(response.feedbacks()).hasSize(1);
            assertThat(response.feedbacks().get(0).content()).isEqualTo("ERD 설계 및 API 명세 작성");
        }

        @Test
        @DisplayName("피드백이 없는 경우 빈 리스트 반환")
        void getMentoringDetail_no_feedbacks() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(MENTOR_ENTITY_ID)).willReturn(Optional.of(
                    User.builder().email("mentor@test.com").password("password")
                            .nickname("김철수").role(UserRole.AUTHOR).build()
            ));
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(mentee));
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of());

            // when
            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(response.feedbacks()).isEmpty();
            assertThat(response.totalSessions()).isEqualTo(0);
        }

        @Test
        @DisplayName("멘토가 탈퇴한 경우 알 수 없는 사용자 반환")
        void getMentoringDetail_deleted_mentor() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(MENTOR_ENTITY_ID)).willReturn(Optional.empty());
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(mentee));
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of());

            // when
            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(response.mentorName()).isEqualTo("알 수 없는 사용자");
        }

        @Test
        @DisplayName("멘티가 탈퇴한 경우 알 수 없는 사용자 반환")
        void getMentoringDetail_deleted_mentee() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(MENTOR_ENTITY_ID)).willReturn(Optional.of(
                    User.builder().email("mentor@test.com").password("password")
                            .nickname("김철수").role(UserRole.AUTHOR).build()
            ));
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.empty());
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of());

            // when
            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, MENTOR_ENTITY_ID);

            // then
            assertThat(response.menteeName()).isEqualTo("알 수 없는 사용자");
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void getMentoringDetail_not_found_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.getMentoringDetail(MENTORING_ID, MENTOR_ENTITY_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void getMentoringDetail_unauthorized_throws() {
            // given
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.getMentoringDetail(MENTORING_ID, 999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }
    }
    @Nested
    @DisplayName("멘토링 피드백 작성")
    class CreateFeedbackTest {

        @Test
        @DisplayName("정상 피드백 작성 - 세션 증가 및 피드백 저장")
        void createFeedback_success() {
            // given
            mentorship.approve();
            MentoringFeedbackRequest request = new MentoringFeedbackRequest("ERD 설계 및 API 명세 작성");
            MentorFeedback feedback = MentorFeedback.create(MENTORING_ID, MENTOR_ENTITY_ID, "ERD 설계 및 API 명세 작성");
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(mentorFeedbackRepository.save(any())).willReturn(feedback);

            // when
            MentoringFeedbackResponse response = mentoringService.createFeedback(
                    MENTORING_ID, MENTOR_ENTITY_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.content()).isEqualTo("ERD 설계 및 API 명세 작성");
            assertThat(mentorship.getTotalSessions()).isEqualTo(1);
            verify(mentorFeedbackRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void createFeedback_mentoring_not_found_throws() {
            // given
            MentoringFeedbackRequest request = new MentoringFeedbackRequest("ERD 설계 및 API 명세 작성");
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mentoringService.createFeedback(MENTORING_ID, MENTOR_ENTITY_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void createFeedback_unauthorized_throws() {
            // given
            MentoringFeedbackRequest request = new MentoringFeedbackRequest("ERD 설계 및 API 명세 작성");
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.createFeedback(MENTORING_ID, 999L, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("진행 중이 아닌 멘토링에 피드백 작성 시 예외 발생")
        void createFeedback_not_accepted_throws() {
            // given
            MentoringFeedbackRequest request = new MentoringFeedbackRequest("ERD 설계 및 API 명세 작성");
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            // when & then
            assertThatThrownBy(() -> mentoringService.createFeedback(MENTORING_ID, MENTOR_ENTITY_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_FEEDBACK_ONLY_ACCEPTED.getMessage());
        }
    }
}