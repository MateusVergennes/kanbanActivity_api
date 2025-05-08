package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.BoardSnapshot;
import com.kanban_api.kanban_api.models.QaSummary;
import com.kanban_api.kanban_api.services.QualityAssuranceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/kanban/cards")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Quality Assurance")
public class QualityAssuranceController {

    @Autowired private QualityAssuranceService qaService;

    @Operation(summary = "Gera relatório de Quality Assurance (cards com subtasks)")
    @GetMapping("/qualityAssuranceReport")
    public QaSummary generateQualityAssuranceReport(
            @Parameter(description = "Considerar apenas os cards com PRs")
            @RequestParam(name="filter_github", defaultValue="true")
            boolean filterGithub,
            @Parameter(description = "Data de fim (YYYY-MM-DD)")
            @RequestParam(name = "created_from_date", required = false)
            String createdFromDate
    ) {
        return qaService.generateReport(filterGithub, createdFromDate);
    }

    @Operation(summary = "Tira um snapshot das colunas (cards por coluna / tag / dev)")
    @GetMapping("/boardSnapshot")
    public BoardSnapshot generateBoardSnapshot(
            @Parameter(description = "Considerar apenas os cards com PRs")
            @RequestParam(name="filter_github", defaultValue="true")
            boolean filterGithub,
            @Parameter(description = "Data mínima de criação (YYYY-MM-DD)")
            @RequestParam(name = "created_from_date", required = false)
            String createdFromDate
    ) {
        return qaService.generateBoardSnapshot(filterGithub, createdFromDate);
    }
}
