package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.domain.user.dto.request.AdminSignupRequest;
import com.example.hot6novelcraft.domain.user.dto.request.AuthorSignupRequest;
import com.example.hot6novelcraft.domain.user.dto.request.CommonSignupRequest;
import com.example.hot6novelcraft.domain.user.dto.request.ReaderSignupRequest;
import com.example.hot6novelcraft.domain.user.dto.response.AdminSignupResponse;
import com.example.hot6novelcraft.domain.user.dto.response.SignupResponse;
import com.example.hot6novelcraft.domain.user.entity.AuthorProfile;
import com.example.hot6novelcraft.domain.user.entity.ReaderProfile;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.userEnum.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.ReaderProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j(topic = "SignupService")
@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final AuthorProfileRepository authorProfileRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /* ======== 중복 확인 ========
    1. 이메일 중복 확인
    2. 닉네임 중복 확인
    ============================= */

    public void checkEmail(String email) {
        if(userRepository.existsByEmail(email)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_EMAIL_ALREADY_EXISTS);
        }
    }

    public void checkNickname(String nickname) {
        if(userRepository.existsByNickname(nickname)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_NICKNAME_ALREADY_EXISTS);
        }
    }

    /* ======== TODO 휴대폰 인증 ========
    1. 인증번호 발송
    2. 인증번호 확인
    ============================= */


    /* ======== 회원 가입 ========
    1. 공통 회원가입 - 독자/작가 추가 정보 기입까지 완료 후, DB 저장 및 임시 JWT 발급으로 보안 설정
    2. 독자 회원가입 - 임시 JWT로만 접근 가능
    3. 작가 회원가입 - 임시 JWT로만 접근 가능
    4. 관리자 회원가입 - 이메일, 비밀번호, 핸드폰 인증만 진행
    ============================= */

    @Transactional
    public String commonSignup(CommonSignupRequest request) {

        // 이메일 및 닉네임 중복 확인
        checkEmail(request.email());
        checkNickname(request.nickname());

        // TODO 휴대폰 인증 완료 여부 확인

        String encoderPassword = passwordEncoder.encode(request.password());
        User user = User.register(
                request.email(),
                encoderPassword,
                request.nickname(),
                request.phoneNo(),
                request.birthDay(),
                UserRole.TEMP           // 임시 역할 부여
        );

        userRepository.save(user);

        log.info("공통 회원가입 완료 - email: {}", request.email());

        // 임시 JWT 유저 저장 (독자/작가 추가 가입 전 임시 상태)
        return jwtUtil.createTempToken(request.email());
    }

    @Transactional
    public SignupResponse readerSignup(ReaderSignupRequest request, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if(!user.getRole().equals(UserRole.TEMP)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP);
        }

        user.changeRole(UserRole.READER);

        ReaderProfile readerProfile = ReaderProfile.register(
                user.getId(),
                request.mainGenreToString(),
                request.readingGoal()
        );
        readerProfileRepository.save(readerProfile);

        log.info("독자 정보 추가 가입 완료 - email: {}", email);

        return SignupResponse.of(user);
    }

    @Transactional
    public SignupResponse authorSignup(AuthorSignupRequest request, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (!user.getRole().equals(UserRole.TEMP)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP);
        }

        user.changeRole(UserRole.AUTHOR);

        AuthorProfile authorProfile = AuthorProfile.register(
                user.getId(),
                request.bio(),
                request.careerLevel(),
                request.mainGenreToString(),
                request.instagramLinks(),
                request.xLinks(),
                request.blogLinks(),
                request.allowMenteeRequest()
        );
        authorProfileRepository.save(authorProfile);

        log.info("작가 정보 추가 가입 완료 - email: {}", email);

        return SignupResponse.of(user);
    }

    @Transactional
    public AdminSignupResponse adminSignup(AdminSignupRequest request, String email) {

        // 이메일 중복 확인
        checkEmail(request.email());

        /*
        1. TODO 휴대폰 인증 완료 여부 확인
        2. TODO admin 전용 Entity 분리??
            현재 : nickname("ADMIN_" + email), birthday(null) 로 저장
         */

        String encoderPassword = passwordEncoder.encode(request.password());

        User admin = User.registerAdmin(
                request.email(),
                encoderPassword,
                request.phoneNo(),
                UserRole.ADMIN
        );
        userRepository.save(admin);

        log.info("관리자 가입 완료 - email: {}", email);

        return AdminSignupResponse.of(admin);
    }


    // TODO ======== 소셜 회원가입 ========
}
