package com.example.hot6novelcraft.domain.admin.scheduler;

import com.example.hot6novelcraft.domain.admin.entity.AdminStatistics;
import com.example.hot6novelcraft.domain.admin.repository.AdminStatisticsRepository;
import com.example.hot6novelcraft.domain.admin.service.AdminCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j( topic = "AdminStatsScheduler")
@Component
@RequiredArgsConstructor
public class AdminStatsScheduler {

    private final AdminCacheService adminCacheService;
    private final AdminStatisticsRepository statisticRepository;

    @Scheduled(cron = "55 59 23 * * *")
    public void saveDailyStatistics() {
        LocalDate today = LocalDate.now();
        log.info("[통계 저장 스케쥴러] {} 일자 대시보드 통계 마감 저장 시작", today);

        // Redis에서 오늘 23시 59분까지 모인 최종 카운트 가져오기
        Long finalUserCount = adminCacheService.getNewUsersToday();
        Long finalNovelCount = adminCacheService.getNewNovelsToday();
        Long finalMentoCount = adminCacheService.getNewMentoToday();

        // DB에 영구 보존
        AdminStatistics dailyStats = AdminStatistics.builder()
                .statsDate(today)
                .newUserCount(finalUserCount)
                .newNovelCount(finalNovelCount)
                .newMentorCount(finalMentoCount)
                .build();

        statisticRepository.save(dailyStats);
        log.info("[통계 저장 스케쥴러] {} 일자 통계 저장 완료 (User:{}, Novel:{}", today, finalUserCount, finalNovelCount);
    }
}
