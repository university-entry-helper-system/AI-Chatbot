package com.khoipd8.educationchatbot.repository;

import com.khoipd8.educationchatbot.entity.CombinationScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CombinationScoreRepository extends JpaRepository<CombinationScore, Long> {
    
    List<CombinationScore> findBySbd(String sbd);
    
    List<CombinationScore> findByCombinationCode(String combinationCode);
    
    List<CombinationScore> findBySbdAndCombinationCode(String sbd, String combinationCode);
    
    List<CombinationScore> findByTotalScoreBetween(Double minScore, Double maxScore);
    
    @Modifying
    @Transactional
    void deleteBySbd(String sbd);
    
    @Query("SELECT cs FROM CombinationScore cs WHERE cs.totalScore >= :minScore ORDER BY cs.totalScore DESC")
    List<CombinationScore> findByMinScore(@Param("minScore") Double minScore);
    
    @Query("SELECT DISTINCT cs.combinationCode FROM CombinationScore cs")
    List<String> findAllCombinationCodes();
}