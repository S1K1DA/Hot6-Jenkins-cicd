package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.user.entity.userEnum.ReadingGoal;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "reader_profiles")
public class ReaderProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String preferredGenres;

    @Enumerated(EnumType.STRING)
    private ReadingGoal readingGoal;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    private ReaderProfile(Long userId, String preferredGenres, ReadingGoal readingGoal) {
        this.userId = userId;
        this.preferredGenres = preferredGenres;
        this.readingGoal = readingGoal;
    }

    @Builder
    public static ReaderProfile register(
            Long userId
            , String preferredGenres
            , ReadingGoal readingGoal
    ) {
        return new ReaderProfile(userId, preferredGenres, readingGoal);
    }
}
