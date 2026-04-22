package com.example.hot6novelcraft.domain.subscription.repository;

import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserIdAndSubscriptionStatus(Long userId, SubscriptionStatus status);

    Optional<Subscription> findByBillingKey(String billingKey);

    Optional<Subscription> findBySubscriptionKey(String subscriptionKey);

    List<Subscription> findAllBySubscriptionStatusAndNextBillingAtBefore(
            SubscriptionStatus status, LocalDateTime dateTime);

    List<Subscription> findAllBySubscriptionStatusAndCreatedAtBefore(
            SubscriptionStatus status, LocalDateTime dateTime);

    Page<Subscription> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
