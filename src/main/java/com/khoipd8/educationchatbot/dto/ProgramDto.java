package com.khoipd8.educationchatbot.dto;

import lombok.Data;

@Data
public class ProgramDto {
    private String name;
    private String code;
    private String subjectCombination;
    private String admissionMethod;
    private Double benchmarkScore2024;
    private Double benchmarkScore2023;
    private Integer quota;
    private String note;
    private String universityCode;
}