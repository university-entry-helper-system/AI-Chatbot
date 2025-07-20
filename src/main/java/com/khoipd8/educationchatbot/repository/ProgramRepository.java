package com.khoipd8.educationchatbot.repository;

import com.khoipd8.educationchatbot.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {

    List<Program> findByNameContainingIgnoreCase(String name);

    List<Program> findByUniversityId(Long universityId);
    
    List<Program> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);

    @Query("SELECT p FROM Program p WHERE p.benchmarkScore2024 BETWEEN :minScore AND :maxScore")
    List<Program> findByScoreRange(@Param("minScore") Double minScore, @Param("maxScore") Double maxScore);

    @Query("SELECT p FROM Program p WHERE p.subjectCombination LIKE %:combination%")
    List<Program> findBySubjectCombination(@Param("combination") String combination);

    @Query("SELECT p FROM Program p JOIN p.university u WHERE u.location LIKE %:location%")
    List<Program> findByUniversityLocation(@Param("location") String location);

    @Query("SELECT DISTINCT p.admissionMethod FROM Program p WHERE p.admissionMethod IS NOT NULL")
    List<String> findAllAdmissionMethods();

    @Query("SELECT p FROM Program p WHERE p.benchmarkScore2024 IS NOT NULL ORDER BY p.benchmarkScore2024 DESC")
    List<Program> findAllOrderByScore2024Desc();
}
