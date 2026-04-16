package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelCreateRequest;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelUpdateRequest;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelCreateResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelDeleteResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelListResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelUpdateResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.userEnum.UserRole;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelRepository novelRepository;
    private final UserRepository userRepository;

    // 소설 등록
    @Transactional
    public NovelCreateResponse createNovel(NovelCreateRequest request, UserDetailsImpl userDetails) {

        User user = userDetails.getUser();

        // 작가 권한 확인
        if (user.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }

        Novel novel = Novel.createNovel(
                user.getId(),
                request.title(),
                request.description(),
                request.genre().toString(),
                request.tagsToString()
        );

        // DB 저장
        Novel savedNovel = novelRepository.save(novel);

        return NovelCreateResponse.from(savedNovel.getId());
    }

    // 소설 수정
    @Transactional
    public NovelUpdateResponse updateNovel(Long novelId, NovelUpdateRequest request, UserDetailsImpl userDetails) {

        User user = userDetails.getUser();

        // 작가 권한 확인
        if (user.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }

        // 소설 조회 공통 메서드(본인 소설 및 삭제여부)
        Novel novel = findNovelById(novelId, user.getId());


        // 소설 수정
        novel.update(
                request.title(),
                request.description(),
                request.genre().toString(),
                request.tagsToString()
        );

        return NovelUpdateResponse.from(novel.getId());
    }

    // 소설 삭제
    @Transactional
    public NovelDeleteResponse deleteNovel(Long novelId, UserDetailsImpl userDetails) {

        User user = userDetails.getUser();

        // 작가 권한 확인
        if (user.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }

        // 소설 조회 공통 메서드(본인 소설 및 삭제여부)
        Novel novel = findNovelById(novelId, user.getId());

        // 소설 삭제(소프트 딜리트)
        novel.delete();

        return NovelDeleteResponse.from(novel.getId());
    }

    // 소설 목록 조회 V1(JPA)
    @Transactional(readOnly = true)
    public PageResponse<NovelListResponse> getNovelListV1(Pageable pageable) {

        Page<Novel> novels = novelRepository.findAllByIsDeletedFalse(pageable);

        Page<NovelListResponse> response = novels.map(novel -> {
            // N+1 문제 발생 가능 - V2에서 QueryDSL로 개선 예정
            String authorNickname = userRepository.findById(novel.getAuthorId())
                    .map(user -> user.getNickname())
                    .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

            return NovelListResponse.of(
                    novel.getId(),
                    novel.getTitle(),
                    novel.getGenre(),
                    novel.getTags(),
                    novel.getStatus(),
                    novel.getCoverImageUrl(),
                    novel.getViewCount(),
                    novel.getBookmarkCount(),
                    authorNickname
            );
        });

        return PageResponse.register(response);
    }

    // 소설 조회 공통 메서드(본인 소설 및 삭제여부)
    private Novel findNovelById(Long novelId, Long userId) {

        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 본인 소설 확인 먼저
        if (!Objects.equals(novel.getAuthorId(), userId)) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_FORBIDDEN);
        }

        // 삭제 여부
        if (novel.isDeleted()) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_ALREADY_DELETED);
        }

        return novel;
    }
}