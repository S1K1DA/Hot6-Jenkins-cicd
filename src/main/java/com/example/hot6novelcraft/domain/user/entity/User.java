package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.user.entity.userEnum.UserRole;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String phoneNo;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    private String refreshToken;

    private boolean isDeleted;

    private LocalDateTime deletedAt;

    private LocalDateTime updatedAt;

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private User(String email, String password, String nickname, String phoneNo, LocalDate birthday, UserRole role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNo = phoneNo;
        this.birthday = birthday;
        this.role = role;
    }

    @Builder
    public static User register(String email, String password, String nickname, String phoneNo, LocalDate birthday, UserRole role) {
        return new User(email, password, nickname, phoneNo, birthday, role);
    }

    // 관리자 전용 메서드
    private User(String email, String password, String phoneNo, UserRole role) {
        this.email = email;
        this.password = password;
        this.phoneNo = phoneNo;
        this.role = role;
    }

    @Builder
    public static User registerAdmin(String email, String password, String phoneNo, UserRole role) {
        return new User(
                email,
                password,
                "ADMIN_" + email,
                phoneNo,
                null,
                UserRole.ADMIN
        );
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }
}
