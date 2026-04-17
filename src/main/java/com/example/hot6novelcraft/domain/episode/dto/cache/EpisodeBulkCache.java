package com.example.hot6novelcraft.domain.episode.dto.cache;

import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;

import java.io.Serializable;

public record EpisodeBulkCache(

        Long episodeId,
        Long novelId,
        int episodeNumber,
        String title,
        String content,
        Long likeCount,
        boolean isFree,
        int pointPrice

) implements Serializable {

    public static EpisodeBulkCache from(Episode episode) {
        return new EpisodeBulkCache(
                episode.getId(),
                episode.getNovelId(),
                episode.getEpisodeNumber(),
                episode.getTitle(),
                episode.getContent(),
                episode.getLikeCount(),
                episode.isFree(),
                episode.getPointPrice()
        );
    }
}