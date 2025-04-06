package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.CustomField;
import com.kanban_api.kanban_api.models.User;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável por escrever, na mesma Sheet, a parte "individual por Dev" logo abaixo
 * do relatório principal. Anexa blocos no final da aba (sem criar arquivo separado).
 */
@Service
public class DevIndividualReportService {

    private static final int STIPULATED_HOURS_ID = 9;
    private static final int TITLE_FIELD_ID = 13;

    @Autowired
    private IntervalProgressService intervalProgressService;

    /**
     * Adiciona, "abaixo" da planilha em uso, a listagem individual por desenvolvedor.
     * @param sheet planilha do Excel (já criada pelo ExcelService)
     * @param cards todos os cards
     * @param from data/hora inicial do intervalo
     * @param to data/hora final do intervalo
     * @param allUsers lista de usuários (para descobrir nome real)
     * @param startRowIndex a linha a partir da qual começamos a escrever
     */
    public void appendByDevBelow(
            Sheet sheet,
            List<Card> cards,
            LocalDateTime from,
            LocalDateTime to,
            List<User> allUsers,
            int startRowIndex
    ) {
        // Agrupa por userId
        Map<Integer, List<Card>> cardsByUser = cards.stream()
                .collect(Collectors.groupingBy(Card::ownerUserId));

        // Mapa userId -> nome
        Map<Long, String> userMap = allUsers.stream()
                .collect(Collectors.toMap(User::user_id, User::realname, (u1,u2)->u1));

        int currentRow = startRowIndex;

        // Para cada dev, cria um bloco
        for (Map.Entry<Integer, List<Card>> entry : cardsByUser.entrySet()) {
            int userId = entry.getKey();
            if (userId <= 0) continue; // ignora user anômalo

            List<Card> devCards = entry.getValue();
            if (devCards.isEmpty()) continue;

            // Nome do Dev
            String devName = userMap.getOrDefault((long) userId, "Desconhecido " + userId);

            // 1) Linha com "Desenvolvedor: X"
            Row devNameRow = sheet.createRow(currentRow++);
            devNameRow.createCell(0).setCellValue("Desenvolvedor: " + devName);

            // 2) Cabeçalho
            currentRow++;
            Row header = sheet.createRow(currentRow - 1);
            header.createCell(0).setCellValue("Chamado");
            header.createCell(1).setCellValue("Título");
            header.createCell(2).setCellValue("Horas Estipuladas");
            header.createCell(3).setCellValue("In Progress Interval");

            // Para calcular total
            double totalHours = 0.0;
            long totalInProgressSeconds = 0;

            // 3) Linhas dos cards
            for (Card c : devCards) {
                Row row = sheet.createRow(currentRow++);
                // Chamado
                row.createCell(0).setCellValue(c.customId());

                // Título
                String finalTitle = retrieveTitle(c);
                row.createCell(1).setCellValue(finalTitle);

                // Horas Estipuladas
                double stipulated = parseStipulatedHours(getStipulatedHoursValue(c));
                row.createCell(2).setCellValue(stipulated);
                totalHours += stipulated;

                // In Progress Interval no período
                long partialSeconds = intervalProgressService.getInProgressWithinPeriod(c, from, to);
                row.createCell(3).setCellValue(formatSeconds(partialSeconds));
                totalInProgressSeconds += partialSeconds;
            }

            // 4) Totais
            Row totalHoursRow = sheet.createRow(currentRow++);
            totalHoursRow.createCell(0).setCellValue("Total Horas Estipuladas:");
            totalHoursRow.createCell(1).setCellValue(totalHours);

            Row totalInProgressRow = sheet.createRow(currentRow++);
            totalInProgressRow.createCell(0).setCellValue("Total In Progress Interval:");
            totalInProgressRow.createCell(1).setCellValue(formatSeconds(totalInProgressSeconds));

            // pula 2 linhas antes do próximo dev
            currentRow += 2;
        }
    }

    /**
     * Tenta usar campo custom (ID=13). Se não encontrar, usa card.title().
     */
    private String retrieveTitle(Card card) {
        if (card.customFields() != null) {
            for (CustomField cf : card.customFields()) {
                if (cf.fieldId() == TITLE_FIELD_ID && cf.value() != null && !cf.value().isEmpty()) {
                    return cf.value();
                }
            }
        }
        return card.title();
    }

    /**
     * Retorna o valor do campo Horas Estipuladas (customField=9), se existir.
     */
    private String getStipulatedHoursValue(Card card) {
        if (card.customFields() == null) return "";
        for (CustomField cf : card.customFields()) {
            if (cf.fieldId() == STIPULATED_HOURS_ID && cf.value() != null && !cf.value().isEmpty()) {
                return cf.value();
            }
        }
        return "";
    }

    /**
     * Faz parse de texto "2.5" ou "3,5" para Double.
     */
    private double parseStipulatedHours(String hoursStr) {
        if (hoursStr == null || hoursStr.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(hoursStr.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Converte segundos em formato "HHh MMm SSs".
     */
    private String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long remainder = totalSeconds % 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }
}