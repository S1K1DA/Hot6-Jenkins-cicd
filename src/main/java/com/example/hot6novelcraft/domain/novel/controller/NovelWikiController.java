package com.example.hot6novelcraft.domain.novel.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelWikiCreateRequest;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiCreateResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiDeleteResponse;
import com.example.hot6novelcraft.domain.novel.service.NovelWikiService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/novels")
public class NovelWikiController {

    private final NovelWikiService novelWikiService;

    /**
     * 설정집 저장
     * 정은식
     */
    @PostMapping("/{novelId}/wiki")
    public ResponseEntity<BaseResponse<NovelWikiCreateResponse>> createWiki(
            @PathVariable Long novelId,
            @Valid @RequestBody NovelWikiCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        NovelWikiCreateResponse response = novelWikiService.createWiki(novelId, request, userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "설정집 저장 성공", response));
    }

    /**
     * 설정집 삭제
     * 정은식
     */
    @DeleteMapping("/{novelId}/wiki/{wikiId}")
    public ResponseEntity<BaseResponse<NovelWikiDeleteResponse>> deleteWiki(
            @PathVariable Long novelId,
            @PathVariable Long wikiId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        NovelWikiDeleteResponse response = novelWikiService.deleteWiki(novelId, wikiId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "설정집 삭제 성공", response)
        );
    }
}