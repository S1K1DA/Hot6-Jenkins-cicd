package com.example.hot6novelcraft.domain.point.service;

import com.example.hot6novelcraft.common.config.EpisodePurchaseConfig;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodePurchaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.NovelBulkPurchaseResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.point.entity.Point;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointService {

    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final EpisodeRepository episodeRepository;
    private final EpisodePurchaseConfig purchaseConfig;
    private final RedisUtil redisUtil;

    /**
     * 포인트 충전
     * - userId에 해당하는 Point가 없으면 최초 생성
     */
    @Transactional
    public void charge(Long userId, Long amount) {
        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> pointRepository.save(Point.create(userId)));

        point.charge(amount);

        pointHistoryRepository.save(
                PointHistory.create(userId, null, null, amount, PointHistoryType.CHARGE, "포인트 충전")
        );
    }

    /**
     * 포인트 차감 (환불 시 회수)
     */
    @Transactional
    public void deduct(Long userId, Long amount) {
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND));

        if (point.getBalance() < amount) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
        }

        point.deduct(amount);

        pointHistoryRepository.save(
                PointHistory.create(userId, null, null, amount, PointHistoryType.REFUND, "환불 차감")
        );
    }

    /**
     * 포인트 복구 (환불 진행 중 PortOne 오류 발생 시 선차감된 포인트 보상)
     */
    @Transactional
    public void compensateDeduct(Long userId, Long amount) {
        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> pointRepository.save(Point.create(userId)));

        point.charge(amount);

        pointHistoryRepository.save(
                PointHistory.create(userId, null, null, amount, PointHistoryType.CHARGE, "환불 오류 복구")
        );
    }

    /**
     * 포인트 잔액 조회
     */
    @Transactional(readOnly = true)
    public Long getBalance(Long userId) {
        return pointRepository.findByUserId(userId)
                .map(Point::getBalance)
                .orElse(0L);
    }

    /**
     * 회차 단건 구매
     */
    @Transactional
    public EpisodePurchaseResponse purchaseEpisode(Long userId, Long episodeId) {
        log.info("[회차 구매] 요청 userId={} episodeId={}", userId, episodeId);

        String lockKey = "episode:purchase:lock:" + userId + ":" + episodeId;
        if (!redisUtil.acquireLock(lockKey)) {
            log.warn("[회차 구매] Lock 획득 실패 (이미 처리 중) userId={} episodeId={}", userId, episodeId);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING);
        }

        try {
            // 1. Episode 조회 및 검증
            Episode episode = episodeRepository.findById(episodeId)
                    .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));
            validatePurchasable(episode);

            // 2. 중복 구매 체크
            boolean alreadyPurchased = pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(
                    userId, episodeId, PointHistoryType.NOVEL
            );
            if (alreadyPurchased) {
                throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_PURCHASED);
            }

            // 3. 포인트 잔액 조회 및 검증
            Point point = pointRepository.findByUserId(userId)
                    .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND));

            if (point.getBalance() < episode.getPointPrice()) {
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
            }

            // 4. 포인트 차감
            point.deduct((long) episode.getPointPrice());
            log.info("[회차 구매] 포인트 차감 완료 userId={} amount={}P", userId, episode.getPointPrice());

            // 5. PointHistory 기록
            pointHistoryRepository.save(
                    PointHistory.create(
                            userId,
                            episode.getNovelId(),
                            episodeId,
                            (long) episode.getPointPrice(),
                            PointHistoryType.NOVEL,
                            "회차 구매: " + episode.getTitle()
                    )
            );

            log.info("[회차 구매] 구매 완료 userId={} episodeId={} price={}P balance={}P",
                    userId, episodeId, episode.getPointPrice(), point.getBalance());

            // 6. 응답 생성
            return EpisodePurchaseResponse.of(episode, point.getBalance());

        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    /**
     * 소설 전체 구매 (발행된 유료 회차 일괄 구매)
     */
    @Transactional
    public NovelBulkPurchaseResponse purchaseAllEpisodes(Long userId, Long novelId) {
        log.info("[소설 전체 구매] 요청 userId={} novelId={}", userId, novelId);

        String lockKey = "novel:bulk-purchase:lock:" + userId + ":" + novelId;
        if (!redisUtil.acquireLock(lockKey)) {
            log.warn("[소설 전체 구매] Lock 획득 실패 (이미 처리 중) userId={} novelId={}", userId, novelId);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING);
        }

        try {
            // 1. 미구매 회차 목록 조회
            List<Episode> unpurchasedEpisodes = getUnpurchasedEpisodes(userId, novelId);

            if (unpurchasedEpisodes.isEmpty()) {
                throw new ServiceErrorException(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES);
            }

            // 2. 가격 계산 (할인 적용)
            int originalPrice = unpurchasedEpisodes.stream()
                    .mapToInt(Episode::getPointPrice)
                    .sum();

            int discountRate = purchaseConfig.getDiscountRate();
            int discountAmount = originalPrice * discountRate / 100;
            int finalPrice = originalPrice - discountAmount;

            log.info("[소설 전체 구매] 가격 계산 userId={} 원가={}P 할인={}% 최종={}P",
                    userId, originalPrice, discountRate, finalPrice);

            // 3. 포인트 잔액 조회 및 검증
            Point point = pointRepository.findByUserId(userId)
                    .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND));

            if (point.getBalance() < finalPrice) {
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
            }

            // 4. 포인트 차감 (한 번에)
            point.deduct((long) finalPrice);
            log.info("[소설 전체 구매] 포인트 차감 완료 userId={} amount={}P", userId, finalPrice);

            // 5. PointHistory 일괄 저장
            List<PointHistory> histories = unpurchasedEpisodes.stream()
                    .map(ep -> PointHistory.create(
                            userId,
                            novelId,
                            ep.getId(),
                            (long) ep.getPointPrice(),
                            PointHistoryType.NOVEL,
                            String.format("소설 전체 구매 (%d%% 할인)", discountRate)
                    ))
                    .toList();

            pointHistoryRepository.saveAll(histories);

            log.info("[소설 전체 구매] 구매 완료 userId={} novelId={} 회차수={} 최종금액={}P 잔액={}P",
                    userId, novelId, unpurchasedEpisodes.size(), finalPrice, point.getBalance());

            // 6. 응답 생성
            return NovelBulkPurchaseResponse.of(
                    novelId, unpurchasedEpisodes, discountRate, point.getBalance()
            );

        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    /**
     * Episode 구매 가능 여부 검증
     */
    private void validatePurchasable(Episode episode) {
        // 무료 회차 체크
        if (episode.isFree()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_FREE_NO_PURCHASE);
        }

        // 발행 상태 체크
        if (episode.getStatus() != EpisodeStatus.PUBLISHED) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE);
        }

        // 삭제 여부 체크
        if (episode.isDeleted()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE);
        }
    }

    /**
     * 미구매 회차 목록 조회
     */
    private List<Episode> getUnpurchasedEpisodes(Long userId, Long novelId) {
        // 1. 발행된 유료 회차 목록 조회
        List<Episode> paidEpisodes = episodeRepository.findPublishedPaidEpisodesByNovelId(novelId);

        // 2. 이미 구매한 회차 ID 목록 조회
        List<Long> purchasedIds = pointHistoryRepository.findPurchasedEpisodeIds(
                userId, novelId, PointHistoryType.NOVEL
        );

        // 3. 미구매 회차 필터링
        return paidEpisodes.stream()
                .filter(ep -> !purchasedIds.contains(ep.getId()))
                .toList();
    }
}
