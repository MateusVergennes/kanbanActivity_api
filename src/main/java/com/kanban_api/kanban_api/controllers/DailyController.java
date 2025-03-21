package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.ColumnResponse;
import com.kanban_api.kanban_api.services.DailyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kanban/daily")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Daily")
public class DailyController {

    @Autowired
    private DailyService dailyService;

    @GetMapping
    public ResponseEntity<List<Card>> getDaily(@RequestParam String startDate,
                                               @RequestParam String endDate,
                                               @RequestParam(required = false) List<Long> columnIds,
                                               @RequestParam(required = false) List<Long> userIds) {
        List<Card> cards = dailyService.getDaily(startDate, endDate, columnIds, userIds);
        return  ResponseEntity.status(HttpStatus.OK).body(cards);
    }

    @GetMapping("/columns")
    public ResponseEntity<ColumnResponse> getColumns() {
        return ResponseEntity.status(HttpStatus.OK).body(dailyService.fetchColumns());
    }

}
