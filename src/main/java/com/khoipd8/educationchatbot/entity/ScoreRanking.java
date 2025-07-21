package com.khoipd8.educationchatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "score_rankings")
@Data
@EqualsAndHashCode
public class ScoreRanking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "exam_year", nullable = false)
    private Integer examYear;
    
    @Column(name = "subject_combination", nullable = false)
    private String subjectCombination; // A00, A01, B00, etc.
    
    @Column(name = "region", nullable = false)
    private String region; // Miền Bắc, Miền Nam, Cả nước
    
    @Column(name = "total_score", nullable = false)
    private Double totalScore;
    
    @Column(name = "ranking_position", nullable = false)
    private Integer rankingPosition;
    
    @Column(name = "total_candidates")
    private Integer totalCandidates;
    
    @Column(name = "percentile")
    private Double percentile; // Tỷ lệ phần trăm
    
    @Column(name = "score_frequency")
    private Integer scoreFrequency; // Số thí sinh có cùng điểm
    
    @Column(name = "cumulative_frequency")
    private Integer cumulativeFrequency; // Tích lũy từ điểm này trở lên
    
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