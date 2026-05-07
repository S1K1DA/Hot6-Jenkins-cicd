package com.example.hot6novelcraft.domain.coverai.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.coverai.dto.response.CoverCreateResponse;
import com.example.hot6novelcraft.domain.coverai.service.CoverService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/novels")
public class CoverController {

    private final CoverService coverService;

    @PostMapping("/{novelId}/cover")
    public ResponseEntity<BaseResponse<CoverCreateResponse>> generateCover(
            @PathVariable Long novelId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        CoverCreateResponse response = coverService.generateCover(novelId, userDetails.getUser().getId());
        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 표지가 성공적으로 생성되었습니다", response)
        );
    }
}