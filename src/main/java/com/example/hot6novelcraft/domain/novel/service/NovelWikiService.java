package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.NovelWikiExceptionEnum;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelWikiCreateRequest;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiCreateResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.NovelWiki;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelWikiRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.userEnum.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NovelWikiService {

    private final NovelWikiRepository novelWikiRepository;
    private final NovelRepository novelRepository;

    // 설정집 저장
    @Transactional
    public NovelWikiCreateResponse createWiki(Long novelId, NovelWikiCreateRequest request,
                                              UserDetailsImpl userDetails) {
        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 소설 조회 (본인 소설 및 삭제여부)
        findNovelById(novelId, userDetails.getUser().getId());

        // 설정집 생성
        NovelWiki wiki = NovelWiki.createWiki(
                novelId,
                request.category(),
                request.title(),
                request.content()
        );

        NovelWiki savedWiki = novelWikiRepository.save(wiki);

        return NovelWikiCreateResponse.from(savedWiki.getId());
    }

    // 작가 권한 확인 공통 메서드
    private void validateAuthorRole(UserDetailsImpl userDetails) {
        if (userDetails.getUser().getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }
    }

    // 소설 조회 공통 메서드 (본인 소설 및 삭제여부)
    private Novel findNovelById(Long novelId, Long userId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 본인 소설 검증
        if (!Objects.equals(novel.getAuthorId(), userId)) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_FORBIDDEN);
        }
        // 삭제 여부 검증
        if (novel.isDeleted()) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_ALREADY_DELETED);
        }
        return novel;
    }
}