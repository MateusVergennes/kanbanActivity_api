package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.Transition;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Service
public class IntervalProgressService {

    /**
     * Retorna, em segundos, quanto tempo esse Card passou na coluna "IN PROGRESS" (id=31)
     * **dentro** do período [intervalStart, intervalEnd].
     */
    public long getInProgressWithinPeriod(Card card, LocalDateTime intervalStart, LocalDateTime intervalEnd) {
        if (card.transitions() == null) {
            return 0L;
        }

        long totalSeconds = 0L;
        for (Transition t : card.transitions()) {
            // Se for a coluna 31 (IN PROGRESS), consideramos
            if (t.column_id() == 31) {
                LocalDateTime transitionStart = parseZulu(t.start());
                // Se t.end for null, significa que ainda está em IN PROGRESS agora
                // (ou no último snapshot do Kanban). Então use 'LocalDateTime.now()'
                LocalDateTime transitionEnd = (t.end() == null)
                        ? LocalDateTime.now()
                        : parseZulu(t.end());

                // Verifica se [transitionStart, transitionEnd] cruza [intervalStart, intervalEnd]
                if (transitionEnd.isAfter(intervalStart) && transitionStart.isBefore(intervalEnd)) {
                    // Calcula a interseção real
                    LocalDateTime overlapStart = (transitionStart.isAfter(intervalStart))
                            ? transitionStart
                            : intervalStart;
                    LocalDateTime overlapEnd = (transitionEnd.isBefore(intervalEnd))
                            ? transitionEnd
                            : intervalEnd;

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
        // Converte do Z (UTC) para o fuso horário do Brasil
        return OffsetDateTime.parse(zuluTime)
                .atZoneSameInstant(java.time.ZoneId.of("America/Sao_Paulo"))
                .toLocalDateTime();
    }

}
