package com.example.hot6novelcraft.domain.mentoring.repository;

import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorWithNickname;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomMentorshipRepository {

    Page<MentorWithNickname> findMentorList(String genre, CareerLevel careerLevel, Pageable pageable);
}