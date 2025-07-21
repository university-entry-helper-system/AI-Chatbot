package com.khoipd8.educationchatbot.repository;

import com.khoipd8.educationchatbot.entity.StudentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {
    
    Optional<StudentScore> findBySbd(String sbd);
    
    List<StudentScore> findByExamYear(Integer examYear);
    
    List<StudentScore> findByRegion(String region);
    
    List<StudentScore> findByExamYearAndRegion(Integer examYear, String region);
    
    @Modifying
    @Transactional
    void deleteBySbd(String sbd);
    
    @Query("SELECT COUNT(s) FROM StudentScore s WHERE s.scoreMath IS NOT NULL OR s.scoreLiterature IS NOT NULL")
    long countWithScores();
}