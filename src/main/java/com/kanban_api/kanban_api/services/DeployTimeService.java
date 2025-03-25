package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.Transition;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeployTimeService {

    // DONE, se necessitar mais, apenas inserir em DEPLOY_COLUMNS
    private static final Set<Integer> DEPLOY_COLUMNS = Set.of(32);

    /**
     * Método que percorre todos os cards e identifica o horário de deploy (hora de entrada
     * na coluna 32, 163 ou 164 mais recente), convertendo para Brasília.
     * Retorna um map cardId -> LocalDateTime (em Brasília).
     */
    public Map<Long, LocalDateTime> findDeployTimes(List<Card> cards) {
        Map<Long, LocalDateTime> deployTimes = new HashMap<>();

        for (Card card : cards) {
            if (card.transitions() == null) {
                continue;
            }

            LocalDateTime latestDeployTime = null;

            // Percorre transitions e pega a transição mais recente (maior data de 'start') em colunas de deploy
            for (Transition transition : card.transitions()) {
                if (DEPLOY_COLUMNS.contains(transition.column_id()) && transition.start() != null) {
                    LocalDateTime transitionStartUtc = parseZuluToLocalDateTime(transition.start());
                    if (latestDeployTime == null || transitionStartUtc.isAfter(latestDeployTime)) {
                        latestDeployTime = transitionStartUtc;
                    }
                }
            }

            // Converte para Brasília
            if (latestDeployTime != null) {
                LocalDateTime brtTime = convertToBrasilia(latestDeployTime);
                deployTimes.put(Long.valueOf(card.cardId()), brtTime);
            }
        }

        return deployTimes;
    }

    /**
     * Converte string em formato UTC (Zulu) para LocalDateTime sem fuso, em UTC.
     */
    private LocalDateTime parseZuluToLocalDateTime(String zuluTime) {
        OffsetDateTime odt = OffsetDateTime.parse(zuluTime);
        return odt.toLocalDateTime();
    }

    /**
     * Converte LocalDateTime (assumido UTC) para horário de Brasília.
     */
    private LocalDateTime convertToBrasilia(LocalDateTime utcTime) {
        // Cria um OffsetDateTime assumindo que utcTime está em UTC
        OffsetDateTime odtUtc = utcTime.atOffset(ZoneOffset.UTC);
        // Ajusta para America/Sao_Paulo
        return odtUtc.atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toLocalDateTime();
    }
}
