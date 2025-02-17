package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.CustomField;
import org.springframework.stereotype.Service;

import com.kanban_api.kanban_api.models.Card;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    private static final String OUTPUT_DIR = "output/";

    public void saveToExcel(List<Card> cards, boolean singleSheet, List<String> columnIds) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            if (singleSheet) {
                saveSingleSheetExcel(OUTPUT_DIR + "kanban-report.xlsx", cards);
            } else {
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    String filePath = OUTPUT_DIR + "kanban-report-" + columnId + ".xlsx";
                    saveSingleSheetExcel(filePath, cardsByColumn.getOrDefault(colId, List.of()));
                }
            }

            System.out.println("✅ Excel gerado na pasta: " + OUTPUT_DIR);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar Excel: " + e.getMessage());
        }
    }

    private void saveSingleSheetExcel(String filePath, List<Card> cards) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Relatório");

        createHeader(sheet);

        int rowIndex = 1;
        int totalPontos = 0;

        for (Card card : cards) {
            Row row = sheet.createRow(rowIndex++);

            // Determinar título baseado no `fieldId = 13`, caso exista
            String titulo = card.title();
            for (CustomField field : card.customFields()) {
                if (field.fieldId() == 13 && field.value() != null && !field.value().isEmpty()) {
                    titulo = field.value();
                    break;
                }
            }

            // Chamado = customId
            String chamado = card.customId();

            // Determinar pontos
            int pontos = calcularPontos(card.title());
            totalPontos += pontos;

            // Preencher linha
            row.createCell(0).setCellValue(titulo);
            row.createCell(1).setCellValue(""); // Canal em branco
            row.createCell(2).setCellValue(chamado);
            row.createCell(3).setCellValue(pontos);
        }

        // Adicionar performance
        double performance = (double) totalPontos / (cards.size() * 20) * 100;
        Row finalRow = sheet.createRow(cards.size() + 2);
        finalRow.createCell(0).setCellValue("Desempenho por entrega: " + String.format("%.2f", performance) + "%");

        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        }

        workbook.close();
        System.out.println("✅ Planilha salva: " + filePath);
    }

    private void createHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Título", "Canal", "Chamado", "Pontos"};

        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private int calcularPontos(String titulo) {
        String titleUpper = titulo.toUpperCase();
        if (titleUpper.contains("MELHORIA")) {
            return 20;
        } else if (titleUpper.contains("REQUISIÇÃO")) {
            return 5;
        } else if (titleUpper.contains("INCIDENTE")) {
            return -20;
        }
        return 0;
    }

}
