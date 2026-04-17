package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.novel.entity.Novel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NovelRepository extends JpaRepository<Novel, Long>, CustomNovelRepository  {

    // V1 - 소설 목록 조회 (IsDeleted확인)
    Page<Novel> findAllByIsDeletedFalse(Pageable pageable);

    @Modifying
    @Query("UPDATE Novel n SET n.viewCount = n.viewCount + 1 WHERE n.id = :novelId")
    void incrementViewCount(@Param("novelId") Long novelId);
}
