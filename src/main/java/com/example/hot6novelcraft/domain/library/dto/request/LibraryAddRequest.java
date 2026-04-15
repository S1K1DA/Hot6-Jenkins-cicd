package com.example.hot6novelcraft.domain.library.dto.request;

import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import jakarta.validation.constraints.NotNull;

public record LibraryAddRequest(

        @NotNull(message = "소설 ID는 필수입니다")
        Long novelId,

        @NotNull(message = "소설 제목은 필수입니다")
        String novelTitle,

        @NotNull(message = "작가명은 필수입니다")
        String authorNickname,

        String coverImageUrl,

        @NotNull(message = "서재 타입은 필수입니다")
        LibraryType libraryType
) {}