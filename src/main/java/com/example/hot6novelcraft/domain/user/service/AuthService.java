package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.user.dto.request.LoginUserRequest;
import com.example.hot6novelcraft.domain.user.dto.response.LoginUserResponse;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j(topic = "AuthService")
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final UserCacheService userCacheService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RedisUtil redisUtil;

    /* ======== 로그인 및 로그아웃 ========
    1. 로그인
    2. TODO!! 소셜 로그인
    3. 토큰 재발급
    4. 로그아웃
    =================================== */

    public LoginUserResponse login(LoginUserRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();

        String accessToken = jwtUtil.createAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getEmail());

        long refreshExpiration = jwtUtil.getRefreshExpiration();
        userCacheService.saveRefreshToken(user.getEmail(), refreshToken, refreshExpiration);

        String pureNewRefresh = jwtUtil.substringToken(refreshToken);

        user.updateRefreshToken(pureNewRefresh);

        return LoginUserResponse.of(user, accessToken, refreshToken);
    }

    public void logout(String accessToken, String email) {
        String token = jwtUtil.substringToken(accessToken);
        userCacheService.deleteRefreshToken(email);

        try {
            long expiration = jwtUtil.getExpiration(token);

            log.warn("===== [디버깅] 추출된 만료 시간 숫자: {} =====", expiration);
            if(expiration > 0) {
                redisUtil.setBlackList(token, "Logout", Duration.ofMillis(expiration));
                log.info("===== [블랙리스트 등록] 사용자가 로그아웃하였습니다. 남은 시간: {}ms =====", expiration);
            }
        } catch(Exception e) {
            log.warn("이미 만료된 토큰입니다.");
        }
    }
}
