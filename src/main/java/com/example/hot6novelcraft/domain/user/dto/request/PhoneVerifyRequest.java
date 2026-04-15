package com.example.hot6novelcraft.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneVerifyRequest (

    @NotBlank
    String phoneNo,

    @NotBlank
    @Size(min = 6, max = 6, message = "6자리의 인증번호를 입력해주세요")
    String verificationCode

    ){

}

