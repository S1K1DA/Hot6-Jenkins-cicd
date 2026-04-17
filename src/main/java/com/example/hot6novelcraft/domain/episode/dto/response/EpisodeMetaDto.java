package com.example.hot6novelcraft.domain.episode.dto.response;

import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;

public record EpisodeMetaDto(

        Long id,
        Long novelId,
        int episodeNumber,
        boolean isFree,
        int pointPrice,
        EpisodeStatus status,
        boolean isDeleted

) {
}