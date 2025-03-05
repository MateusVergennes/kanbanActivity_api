package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.CustomField;
import com.kanban_api.kanban_api.models.User;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    private static final String OUTPUT_DIR = "output/";

    public void saveToExcel(List<Card> cards, boolean singleSheet, List<String> columnIds, List<User> allUsers) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            Map<Long, User> userMap = allUsers.stream()
                    .collect(Collectors.toMap(User::user_id, u -> u));

            if (singleSheet) {
                saveSingleSheetExcel(OUTPUT_DIR + "kanban-report.xlsx", cards, userMap);
            } else {
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    String filePath = OUTPUT_DIR + "kanban-report-" + columnId + ".xlsx";
                    List<Card> cardsDaColuna = cardsByColumn.getOrDefault(colId, List.of());
                    saveSingleSheetExcel(filePath, cardsDaColuna, userMap);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar Excel: " + e.getMessage());
        }
    }

    private void saveSingleSheetExcel(String filePath, List<Card> cards, Map<Long, User> userMap) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Relatório");

        cards = cards.stream()
                .sorted(
                        Comparator
                                .comparingInt((Card c) -> getPriority(getTituloFinal(c)))
                                .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                )
                .collect(Collectors.toList());

        createHeader(sheet);

        int rowIndex = 1;
        int totalPontos = 0;

        for (Card card : cards) {
            Row row = sheet.createRow(rowIndex++);

            String tituloFinal = getTituloFinal(card);
            String desenvolvedor = getDeveloperName(card.ownerUserId(), userMap);
            String chamado = card.customId();

            int pontos = calcularPontos(tituloFinal);
            totalPontos += pontos;

            row.createCell(0).setCellValue(tituloFinal);
            row.createCell(1).setCellValue(desenvolvedor);
            row.createCell(2).setCellValue("");
            row.createCell(3).setCellValue(chamado);
            row.createCell(4).setCellValue(pontos);
        }

        double performance = (double) totalPontos / (cards.size() * 20) * 100;
        Row finalRow = sheet.createRow(cards.size() + 2);
        finalRow.createCell(0).setCellValue("Desempenho por entrega: " + String.format("%.2f", performance) + "%");

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    private void createHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Título", "Desenvolvedor", "Canal", "Chamado", "Pontos"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private String getTituloFinal(Card card) {
        String titulo = card.title();
        for (CustomField field : card.customFields()) {
            if (field.fieldId() == 13 && field.value() != null && !field.value().isEmpty()) {
                titulo = field.value();
                break;
            }
        }
        return titulo;
    }

    private String getDeveloperName(int userId, Map<Long, User> userMap) {
        if (userMap == null) {
            return "";
        }
        User user = userMap.get((long) userId); // converte int -> long
        return (user != null) ? user.realname() : "";
    }


    private int calcularPontos(String titulo) {
        String upper = titulo.toUpperCase();
        if (upper.contains("MELHORIA")) {
            return 20;
        }
        if (upper.contains("INCIDENTE") || upper.contains("CORREÇÃO")) {
            return -20;
        }
        if (upper.contains("REQUISIÇÃO") || upper.contains("AJUSTE")) {
            return 5;
        }
        return 0;
    }

    private int getPriority(String titulo) {
        String upper = titulo.toUpperCase();
        if (upper.contains("MELHORIA")) {
            return 1;
        }
        if (upper.contains("INCIDENTE") || upper.contains("CORREÇÃO")) {
            return 2;
        }
        if (upper.contains("REQUISIÇÃO") || upper.contains("AJUSTE")) {
            return 3;
        }
        return 4;
    }
}