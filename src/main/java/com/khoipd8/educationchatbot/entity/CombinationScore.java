package com.khoipd8.educationchatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "combination_scores")
@Data
@EqualsAndHashCode
public class CombinationScore {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "sbd", nullable = false)
    private String sbd;
    
    @Column(name = "combination_code", nullable = false)
    private String combinationCode; // A00, A01, B00, etc.
    
    @Column(name = "combination_name")
    private String combinationName; // Toán, Vật lí, Hóa học
    
    @Column(name = "total_score", nullable = false)
    private Double totalScore;
    
    @Column(name = "region")
    private String region;
    
    // Ranking information
    @Column(name = "rank_position")
    private Integer rankPosition;
    
    @Column(name = "students_with_same_score")
    private Integer studentsWithSameScore;
    
    @Column(name = "students_with_higher_score")
    private Integer studentsWithHigherScore;
    
    @Column(name = "total_students_in_combination")
    private Integer totalStudentsInCombination;
    
    @Column(name = "equivalent_score_2024")
    private Double equivalentScore2024;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_score_id")
    private StudentScore studentScore;
}