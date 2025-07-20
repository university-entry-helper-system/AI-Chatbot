package com.khoipd8.educationchatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "programs")
@Data
@EqualsAndHashCode(exclude = {"university"})
@ToString(exclude = {"university"})
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String code;

    @Column(name = "subject_combination")
    private String subjectCombination;

    @Column(name = "admission_method")
    private String admissionMethod;

    @Column(name = "benchmark_score_2024")
    private Double benchmarkScore2024;

    @Column(name = "benchmark_score_2023")
    private Double benchmarkScore2023;

    @Column(name = "benchmark_score_2022")
    private Double benchmarkScore2022;

    private Integer quota;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "career_prospects", columnDefinition = "TEXT")
    private String careerProspects;

    @Column(name = "tuition_fee")
    private String tuitionFee;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

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