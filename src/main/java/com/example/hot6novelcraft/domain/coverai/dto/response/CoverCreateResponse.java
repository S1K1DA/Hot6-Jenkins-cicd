package com.example.hot6novelcraft.domain.coverai.dto.response;

public record CoverCreateResponse(
        Long novelId,
        String coverImageUrl
) {
    public static CoverCreateResponse of(Long novelId, String coverImageUrl) {
        return new CoverCreateResponse(novelId, coverImageUrl);
    }
}