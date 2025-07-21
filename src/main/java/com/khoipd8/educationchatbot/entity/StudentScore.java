package com.khoipd8.educationchatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_scores")
@Data
@EqualsAndHashCode
public class StudentScore {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "sbd", nullable = false, unique = true)
    private String sbd; // Số báo danh
    
    @Column(name = "exam_year", nullable = false)
    private Integer examYear;
    
    @Column(name = "region")
    private String region; // Miền Bắc, Miền Nam, Toàn quốc
    
    // Điểm các môn thi
    @Column(name = "score_math")
    private Double scoreMath;
    
    @Column(name = "score_literature")
    private Double scoreLiterature;
    
    @Column(name = "score_english")
    private Double scoreEnglish;
    
    @Column(name = "score_physics")
    private Double scorePhysics;
    
    @Column(name = "score_chemistry")
    private Double scoreChemistry;
    
    @Column(name = "score_biology")
    private Double scoreBiology;
    
    @Column(name = "score_history")
    private Double scoreHistory;
    
    @Column(name = "score_geography")
    private Double scoreGeography;
    
    @Column(name = "score_civic_education")
    private Double scoreCivicEducation;
    
    // Tổ hợp thi có thể xét
    @Column(name = "eligible_combinations")
    private String eligibleCombinations; // A00,C01,C02,C05
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}