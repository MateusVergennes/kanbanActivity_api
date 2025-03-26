package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.Transition;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FilterIntervalTransitionsService {

    /**
     * Verifica se, em alguma transição, o INÍCIO ou FIM cai dentro do intervalo [start, end].
     */
    private boolean hasAnyTransitionBoundaryInInterval(Card card, LocalDateTime start, LocalDateTime end) {
        if (card.transitions() == null || card.transitions().isEmpty()) {
            return false;
        }

        for (Transition transition : card.transitions()) {
            LocalDateTime transitionStart = parseZuluToBrasilia(transition.start());
            LocalDateTime transitionEnd   = (transition.end() == null)
                    ? LocalDateTime.now()  // se end=null, consideramos "até agora"
                    : parseZuluToBrasilia(transition.end());

            if (transitionBoundaryInsideInterval(transitionStart, transitionEnd, start, end)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retorna true se a transição (start1, end1) tiver INÍCIO ou FIM dentro
     * do intervalo [start2, end2].
     */
    private boolean transitionBoundaryInsideInterval(
            LocalDateTime start1,
            LocalDateTime end1,
            LocalDateTime start2,
            LocalDateTime end2
    ) {
        // Ajustar end1=null conforme conveniência
        if (end1 == null) {
            end1 = LocalDateTime.now();
        }

        boolean startIn = !start1.isBefore(start2) && !start1.isAfter(end2);
        boolean endIn   = !end1.isBefore(start2)   && !end1.isAfter(end2);

        return startIn || endIn;
    }

    /**
     * Converte data/hora em string UTC (Zulu) para o fuso de Brasília.
     */
    private LocalDateTime parseZuluToBrasilia(String zuluTime) {
        if (zuluTime == null) return null;
        OffsetDateTime odt = OffsetDateTime.parse(zuluTime);
        return odt.atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toLocalDateTime();
    }
    public List<Card> filterCardsWithoutTransitionsInPeriod(
            List<Card> cards,
            LocalDate start,
            LocalDate end
    ) {
        LocalDateTime startOfDay = start.atStartOfDay();
        LocalDateTime endOfDay   = end.atTime(23, 59, 59);

        return cards.stream()
                .filter(card -> hasAnyTransitionBoundaryInInterval(card, startOfDay, endOfDay))
                .collect(Collectors.toList());
    }

}
