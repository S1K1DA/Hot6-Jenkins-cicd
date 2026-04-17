package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.user.dto.request.AuthorRequest;
import com.example.hot6novelcraft.domain.user.dto.request.CommonSignupRequest;
import com.example.hot6novelcraft.domain.user.dto.request.SocialSignupRequest;
import com.example.hot6novelcraft.domain.user.dto.response.SocialSignupResponse;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.SocialAuthRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignupService 단위 테스트")
class SignupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorProfileRepository authorProfileRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SocialAuthRepository socialAuthRepository;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private SignupService signupService;

    // ==================== 공통 픽스처 ====================
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PHONE = "01012345678";
    private static final String TEST_NICKNAME = "테스터";
    private static final String TEST_PASSWORD = "password123!";
    private static final String REDIS_VERIFIED_KEY = "SMS:VERIFIED:" + TEST_PHONE;
    private static final String FAKE_TEMP_TOKEN = "Bearer eyJhbGci.test.token";

    private User createUser(boolean isDeleted) {
        User user = User.register(
                TEST_EMAIL, "encodedPassword", TEST_NICKNAME, TEST_PHONE,
                LocalDate.of(1995, 1, 1), UserRole.READER
        );
        if (isDeleted) {
            user.withdraw(); // 엔티티에 구현한 탈퇴 로직 호출 (isDeleted=true 세팅)
        }
        return user;
    }

    private User createAutheorUser() {
        return User.register(
                TEST_EMAIL,
                "encodedPassword",
                TEST_NICKNAME,
                TEST_PHONE,
                LocalDate.of(1995, 1, 1),
                UserRole.READER
        );
    }

    // ==================== Redis SMS 인증 공통 세팅 ====================
    private void mockSmsVerified(String phoneNo) {
        String key = "SMS:VERIFIED:" + phoneNo;
        given(redisUtil.get(key)).willReturn("TRUE");
    }

    private void mockSmsNotVerified(String phoneNo) {
        String key = "SMS:VERIFIED:" + phoneNo;
        given(redisUtil.get(key)).willReturn(null);
    }

    // ====================================================================
    // 공통 회원가입 테스트
    // ====================================================================
    @Nested
    @DisplayName("공통 회원가입 (commonSignup)")
    class CommonSignupTest {

        private CommonSignupRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new CommonSignupRequest(
                    TEST_EMAIL,
                    TEST_PASSWORD,
                    TEST_NICKNAME,
                    LocalDate.of(1995, 1, 1),
                    TEST_PHONE
            );
        }

        @Test
        @DisplayName("[성공] SMS 인증 완료 + 중복 없음 → 임시 토큰 반환")
        void commonSignup_success() {

            // given
            mockSmsVerified(TEST_PHONE);

            // exists 대신 findByEmail/Nickname이 Optional.empty()를 반환하도록 세팅
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());
            given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.empty());
            given(passwordEncoder.encode(TEST_PASSWORD)).willReturn("encodedPassword");
            given(jwtUtil.createTempToken(TEST_EMAIL)).willReturn(FAKE_TEMP_TOKEN);

            String result = signupService.commonSignup(validRequest);

            // when
            assertThat(result).isEqualTo(FAKE_TEMP_TOKEN);

            // then
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("[실패] SMS 인증 미완료 → ERR_PHONE_NOT_VERIFIED 예외")
        void commonSignup_fail_phoneNotVerified() {

            // given
            mockSmsNotVerified(TEST_PHONE);

            // when & then
            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED.getMessage());
        }

        @Test
        @DisplayName("[실패] 이메일 중복 (활성 유저) → ERR_EMAIL_ALREADY_EXISTS")
        void commonSignup_fail_emailDuplicated() {

            // given
            mockSmsVerified(TEST_PHONE);
            // 활성 상태의 유저 존재 가정
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(createUser(false)));

            // when & then
            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_EMAIL_ALREADY_EXISTS.getMessage());
        }

        @Test
        @DisplayName("[실패] 닉네임 중복 (활성 유저) → ERR_NICKNAME_ALREADY_EXISTS")
        void commonSignup_fail_nicknameDuplicated() {
            mockSmsVerified(TEST_PHONE);

            // given
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());
            given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.of(createUser(false)));

            // when & then
            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_NICKNAME_ALREADY_EXISTS.getMessage());
            verify(userRepository, never()).save(any());
        }

        // ==========================================
        // 탈퇴 유예 기간 유저 케이스 (이메일)
        // ==========================================
        @Test
        @DisplayName("[실패] 이메일 중복 (30일 이내 탈퇴 유예 유저) → ERR_USER_WITHDRAWAL_PENDING_CONFLICT")
        void commonSignup_fail_emailWithdrawalPending() {

            mockSmsVerified(TEST_PHONE);
            // 탈퇴 상태의 유저 존재 가정
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(createUser(true)));

            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT.getMessage());
        }
    }

    // ====================================================================
    // 작가 추가 가입 테스트
    // ====================================================================
    @Nested
    @DisplayName("작가 추가 가입 (authorSignup)")
    class AuthorSignupTest {

        private AuthorRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new AuthorRequest(
                    List.of(MainGenre.FANTASY, MainGenre.ROMANCE_FANTASY)
                    , "저는 판타지 작가입니다. 10년째 집필 중입니다."
                    , CareerLevel.INTERMEDIATE
                    , null
                    , null
                    , null
                    , true
            );
        }

        @Test
        @DisplayName("[실패] 이미 가입 완료된 AUTHOR 유저 → ERR_ALREADY_COMPLETED_SIGNUP 예외")
        void authorSignup_fail_alreadyCompleted() {

            // given
            User authorUser = User.register(
                    TEST_EMAIL, "encodedPw", TEST_NICKNAME, TEST_PHONE,
                    LocalDate.of(1995, 1, 1), UserRole.AUTHOR
            );
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(authorUser));

            // when & then
            assertThatThrownBy(() -> signupService.authorSignup(validRequest, TEST_EMAIL))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP.getMessage());
        }
    }

    // ====================================================================
    // 소셜 공통 가입 테스트
    // ====================================================================
    @Nested
    @DisplayName("소셜 공통 가입 (socialCommonSignup)")
    class SocialCommonSignupTest {

        private SocialSignupRequest validRequest;
        private static final String PROVIDER_ID = "google-sub-12345";

        @BeforeEach
        void setUp() {
            validRequest = new SocialSignupRequest(
                    TEST_NICKNAME
                    , LocalDate.of(1995,1,1)
                    , TEST_PHONE
            );
        }

        @Test
        @DisplayName("[성공] 소셜 신규 유저 공통 가입 → tempToken 반환")
        void socialCommonSignup_success() {

            // given
            mockSmsVerified(TEST_PHONE);
            given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.ofNullable(null));;

            User socialTempUser = createUser(false);
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(socialTempUser));
            given(jwtUtil.createTempToken(TEST_EMAIL)).willReturn(FAKE_TEMP_TOKEN);

            // when
            SocialSignupResponse response = signupService.socialCommonSignup(
                    validRequest, TEST_EMAIL, PROVIDER_ID, ProviderSns.GOOGLE
            );

            // then
            assertThat(response.tempToken()).isEqualTo(FAKE_TEMP_TOKEN);

            verify(socialAuthRepository, times(1)).save(any());
            verify(redisUtil, times(1)).delete(REDIS_VERIFIED_KEY);
        }

        @Test
        @DisplayName("[실패] SMS 미인증 상태로 소셜 가입 시도 → ERR_PHONE_NOT_VERIFIED 예외")
        void socialCommonSignup_fail_phoneNotVerified() {

            // given
            mockSmsNotVerified(TEST_PHONE);

            // when
            assertThatThrownBy(() -> signupService.socialCommonSignup(
                    validRequest, TEST_EMAIL, PROVIDER_ID, ProviderSns.GOOGLE
            ))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED.getMessage());

            // then
            verify(userRepository, never()).findByEmail(any());
            verify(socialAuthRepository, never()).save(any());
        }

        @Test
        @DisplayName("[실패] 탈퇴 유예 기간(30일 이내) 이메일로 소셜 가입 시도 → ERR_USER_WITHDRAWAL_PENDING_CONFLICT")
        void socialCommonSignup_fail_withdrawalPending() {
            // given
            mockSmsVerified(TEST_PHONE);
            given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.empty()); // 닉네임은 사용 가능

            // DB에 존재하는 유저가 탈퇴 상태(isDeleted=true)인 경우를 가정
            User withdrawnUser = createUser(true);
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(withdrawnUser));

            // when & then
            assertThatThrownBy(() -> signupService.socialCommonSignup(
                    validRequest, TEST_EMAIL, PROVIDER_ID, ProviderSns.GOOGLE
            ))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT.getMessage());
        }

        @Test
        @DisplayName("[실패] 닉네임 중복 → ERR_NICKNAME_ALREADY_EXISTS 예외")
        void socialCommonSignup_fail_nicknameExists() {

        // given
        mockSmsVerified(TEST_PHONE);
        // 닉네임 조회 시 이미 누군가 사용 중임
        given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.of(createUser(false)));

        // when & then
        assertThatThrownBy(() -> signupService.socialCommonSignup(
        validRequest, TEST_EMAIL, PROVIDER_ID, ProviderSns.GOOGLE
        ))
            .isInstanceOf(ServiceErrorException.class)
            .hasMessage(UserExceptionEnum.ERR_NICKNAME_ALREADY_EXISTS.getMessage());
        }
    }
}