package com.khoipd8.educationchatbot.repository;

import com.khoipd8.educationchatbot.entity.ScoreRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreRankingRepository extends JpaRepository<ScoreRanking, Long> {
    
    List<ScoreRanking> findByExamYearAndSubjectCombinationAndRegion(
            Integer examYear, String subjectCombination, String region);
    
    List<ScoreRanking> findByExamYearAndSubjectCombination(
            Integer examYear, String subjectCombination);
    
    @Query("SELECT sr FROM ScoreRanking sr WHERE sr.examYear = :year AND sr.totalScore BETWEEN :minScore AND :maxScore")
    List<ScoreRanking> findByYearAndScoreRange(
            @Param("year") Integer year, 
            @Param("minScore") Double minScore, 
            @Param("maxScore") Double maxScore);
    
    @Query("SELECT sr FROM ScoreRanking sr WHERE sr.examYear = :year AND sr.subjectCombination = :combination AND sr.totalScore <= :score ORDER BY sr.totalScore DESC")
    List<ScoreRanking> findCandidatesWithLowerScores(
            @Param("year") Integer year,
            @Param("combination") String combination,
            @Param("score") Double score);
    
    @Query("SELECT DISTINCT sr.subjectCombination FROM ScoreRanking sr WHERE sr.examYear = :year")
    List<String> findAvailableCombinations(@Param("year") Integer year);
    
    @Query("SELECT DISTINCT sr.region FROM ScoreRanking sr WHERE sr.examYear = :year")
    List<String> findAvailableRegions(@Param("year") Integer year);
}
