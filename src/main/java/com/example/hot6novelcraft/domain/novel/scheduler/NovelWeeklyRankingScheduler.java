package com.example.hot6novelcraft.domain.novel.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NovelWeeklyRankingScheduler {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REALTIME_RANKING_KEY = "ranking:novel:realtime";
    private static final String WEEKLY_RANKING_KEY = "ranking:novel:weekly";

    /**
     * ======= [실시간 랭킹] ============
     * - 매 정각마다 초기화
     * - 11:00:00 에 10시~11시까지의 데이터 초기화
     * 서하나
     * ===================================
     */
    @Scheduled(cron = "0 0 * * * *")
    public void resetRealtimeRanking() {
        redisTemplate.delete(REALTIME_RANKING_KEY);
        log.info("[Redis ZSet 실시간] 실시간 인기 소설 랭킹 초기화 완료");
    }

    /**
     * ======= [주간 랭킹] ============
     * - Redis 주간 키 초기화 및 새 주차 시작
     * - 일요일 00시 01분 업데이트 초기화
     * 서하나
     * ===================================
     */
    @Transactional
    @Scheduled(cron = "0 1 0 * * SUN")
    public void resetWeeklyRanking() {
        redisTemplate.delete(WEEKLY_RANKING_KEY);
        log.info("[Redis ZSet 실시간] 주간 인기 소설 랭킹 초기화 완료, 새 주차 시작");
    }
}
