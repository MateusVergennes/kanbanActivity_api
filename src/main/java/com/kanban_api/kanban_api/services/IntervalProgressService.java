package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.Transition;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Service para calcular intervalos IN PROGRESS dentro de um período.
 */
@Service
public class IntervalProgressService {

    private static final int IN_PROGRESS_COLUMN_ID = 31; // ID da coluna "In Progress"

    /**
     * Retorna, em segundos, quanto tempo o Card passou na coluna "IN PROGRESS" (id=31)
     * dentro do período [intervalStart, intervalEnd].
     */
    public long getInProgressWithinPeriod(Card card, LocalDateTime intervalStart, LocalDateTime intervalEnd) {
        if (card.transitions() == null) {
            return 0L;
        }

        long totalSeconds = 0L;
        for (Transition t : card.transitions()) {
            if (t.column_id() == IN_PROGRESS_COLUMN_ID) {
                LocalDateTime startT = parseZulu(t.start());
                LocalDateTime endT = (t.end() == null)
                        ? LocalDateTime.now()
                        : parseZulu(t.end());

                // Se [startT, endT] cruza [intervalStart, intervalEnd], somamos a parte overlapped
                if (endT.isAfter(intervalStart) && startT.isBefore(intervalEnd)) {
                    LocalDateTime overlapStart = startT.isAfter(intervalStart) ? startT : intervalStart;
                    LocalDateTime overlapEnd = endT.isBefore(intervalEnd) ? endT : intervalEnd;

                    long overlapSec = java.time.Duration.between(overlapStart, overlapEnd).getSeconds();
                    if (overlapSec > 0) {
                        totalSeconds += overlapSec;
                    }
                }
            }
        }
        return totalSeconds;
    }

    private LocalDateTime parseZulu(String zuluTime) {
        return OffsetDateTime.parse(zuluTime)
                .atZoneSameInstant(ZoneId.of("America/Sao_Paulo"))
                .toLocalDateTime();
    }

}