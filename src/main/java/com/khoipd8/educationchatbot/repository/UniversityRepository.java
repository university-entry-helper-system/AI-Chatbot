package com.khoipd8.educationchatbot.repository;

import com.khoipd8.educationchatbot.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UniversityRepository extends JpaRepository<University, Long> {

    Optional<University> findByCode(String code);

    List<University> findByNameContainingIgnoreCase(String name);

    List<University> findByLocationContainingIgnoreCase(String location);
    
    List<University> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);

    @Query("SELECT u FROM University u JOIN FETCH u.programs WHERE u.code = :code")
    Optional<University> findByCodeWithPrograms(@Param("code") String code);

    @Query("SELECT DISTINCT u.location FROM University u WHERE u.location IS NOT NULL")
    List<String> findAllLocations();

    @Query("SELECT COUNT(p) FROM University u JOIN u.programs p WHERE u.id = :universityId")
    Long countProgramsByUniversityId(@Param("universityId") Long universityId);
}
