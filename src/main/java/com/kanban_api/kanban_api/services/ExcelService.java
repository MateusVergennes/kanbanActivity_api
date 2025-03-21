package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    @Autowired
    private TagService tagService;

    @Autowired
    private IntervalProgressService intervalProgressService;

    private static final String OUTPUT_DIR = "output/";

    /**
     * Ordem preferencial das colunas:
     */
    private static final List<String> PREFERRED_ORDER = Arrays.asList(
            "IN PROGRESS",
            "BACKLOG",
            "TO DO",
            "CODE REVIEW",
            "READY FOR QA",
            "QA TEST",
            "READY TO DEPLOY",
            "DEPLOYED",
            "CLIENT DEMO",
            "DONE"
    );

    // ------------------------------------------------------------------------------------
    // Relatório SEMANAL (com pontos, estilo antigo)
    // ------------------------------------------------------------------------------------
    public void saveToExcel(
            List<Card> cards,
            boolean singleSheet,
            List<String> columnIds,
            List<User> allUsers,
            boolean fillChannels,
            boolean includePoints,
            String fileBaseName
    ) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Mapeia userId -> User
            Map<Long, User> userMap = allUsers.stream()
                    .collect(Collectors.toMap(User::user_id, u -> u));

            // Carrega tags
            List<Tag> allTags = tagService.getAllTags();
            Map<Integer, String> tagMap = allTags.stream()
                    .collect(Collectors.toMap(Tag::tag_id, Tag::label));

            // isDevReport = false aqui, pois é o relatório semanal
            boolean isDevReport = false;

            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetExcel(filepath, cards, userMap, tagMap, fillChannels, includePoints, isDevReport);
            } else {
                // Se não for singleSheet, gerar 1 arquivo por coluna
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    List<Card> cardsDaColuna = cardsByColumn.getOrDefault(colId, List.of());

                    String filePath = OUTPUT_DIR + fileBaseName + "-" + columnId + ".xlsx";
                    saveSingleSheetExcel(filePath, cardsDaColuna, userMap, tagMap, fillChannels, includePoints, isDevReport);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Gera UMA planilha Excel para o "weeklyReport"
     */
    private void saveSingleSheetExcel(
            String filePath,
            List<Card> cards,
            Map<Long, User> userMap,
            Map<Integer, String> tagMap,
            boolean fillChannels,
            boolean includePoints,
            boolean isDevReport  // <-- novo parâmetro
    ) throws IOException {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Relatório");

        // Ordenação de exemplo (pode ajustar)
        cards = cards.stream()
                .sorted(
                        Comparator
                                .comparingInt((Card c) -> getPriority(getTituloFinal(c, isDevReport)))
                                .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                )
                .collect(Collectors.toList());

        // Cabeçalho fixo
        createHeader(sheet, includePoints);

        int rowIndex = 1;
        int totalPontos = 0;
        for (Card card : cards) {
            Row row = sheet.createRow(rowIndex++);

            // Agora chamamos getTituloFinal com isDevReport
            String tituloFinal = getTituloFinal(card, isDevReport);
            String dev = getDeveloperName(card.ownerUserId(), userMap);
            String chamado = card.customId();

            // Monta canal
            String canal = "";
            if (fillChannels) {
                List<Integer> cardTagIds = (card.tagIds() != null) ? card.tagIds() : List.of();
                canal = cardTagIds.stream()
                        .map(id -> tagMap.getOrDefault(id, ""))
                        .filter(lbl -> !lbl.isBlank())
                        .collect(Collectors.joining(", "));
            }

            // Calcula pontos?
            int pontos = 0;
            if (includePoints) {
                pontos = calcularPontos(tituloFinal);
                totalPontos += pontos;
            }

            // Preenche células
            row.createCell(0).setCellValue(tituloFinal);
            row.createCell(1).setCellValue(dev);
            row.createCell(2).setCellValue(canal);
            row.createCell(3).setCellValue(chamado);

            if (includePoints) {
                row.createCell(4).setCellValue(pontos);
            }
        }

        // Desempenho
        if (includePoints && !cards.isEmpty()) {
            double performance = (double) totalPontos / (cards.size() * 20) * 100;
            Row finalRow = sheet.createRow(cards.size() + 2);
            finalRow.createCell(0)
                    .setCellValue("Desempenho por entrega: " + String.format("%.2f", performance) + "%");
        }

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHeader(Sheet sheet, boolean includePoints) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Título");
        headerRow.createCell(1).setCellValue("Desenvolvedor");
        headerRow.createCell(2).setCellValue("Canal");
        headerRow.createCell(3).setCellValue("Chamado");
        if (includePoints) {
            headerRow.createCell(4).setCellValue("Pontos");
        }
    }

    // ------------------------------------------------------------------------------------
    // devReport dinâmico (usa lead_time_per_column e columns) com ordem custom
    // ------------------------------------------------------------------------------------
    public void saveToExcelDevDynamic(
            List<Card> cards,
            boolean singleSheet,
            List<String> columnIds,
            List<User> allUsers,
            boolean fillChannels,
            String fileBaseName,
            List<Column> columns,
            LocalDateTime from,
            LocalDateTime to,
            boolean weeklyStipulatedCalculation
    ) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Mapa userId -> User
            Map<Long, User> userMap = allUsers.stream()
                    .collect(Collectors.toMap(User::user_id, u -> u));

            // Mapa tagId -> label
            List<Tag> allTags = tagService.getAllTags();
            Map<Integer, String> tagMap = allTags.stream()
                    .collect(Collectors.toMap(Tag::tag_id, Tag::label));

            // isDevReport = true, pois este método gera o "devReport"
            boolean isDevReport = true;

            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetDevDynamic(filepath, cards, userMap, tagMap, fillChannels, columns, from, to, isDevReport, weeklyStipulatedCalculation);
            } else {
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    List<Card> cardsDaColuna = cardsByColumn.getOrDefault(colId, List.of());

                    String filePath = OUTPUT_DIR + fileBaseName + "-" + columnId + ".xlsx";
                    saveSingleSheetDevDynamic(filePath, cardsDaColuna, userMap, tagMap, fillChannels, columns, from, to, isDevReport, weeklyStipulatedCalculation);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar Excel Dev Dynamic: " + e.getMessage(), e);
        }
    }

    private void saveSingleSheetDevDynamic(
            String filePath,
            List<Card> cards,
            Map<Long, User> userMap,
            Map<Integer, String> tagMap,
            boolean fillChannels,
            List<Column> columns,
            LocalDateTime from,
            LocalDateTime to,
            boolean isDevReport,
            boolean weeklyStipulatedCalculation
    ) throws IOException {

        // 1) Ordena as colunas pela ordem preferencial + alfabético
        List<Column> sortedColumns = new ArrayList<>(columns);
        sortedColumns.sort((c1, c2) -> {
            String n1 = c1.name().toUpperCase();
            String n2 = c2.name().toUpperCase();

            int idx1 = PREFERRED_ORDER.indexOf(n1);
            int idx2 = PREFERRED_ORDER.indexOf(n2);

            // Se ambos estão na lista preferida, compare pelos índices
            if (idx1 >= 0 && idx2 >= 0) {
                return Integer.compare(idx1, idx2);
            }
            // Se apenas um está na preferida, esse vem primeiro
            if (idx1 >= 0 && idx2 < 0) {
                return -1;
            }
            if (idx2 >= 0 && idx1 < 0) {
                return 1;
            }
            // Nenhum na preferida -> comparar por nome
            return n1.compareTo(n2);
        });

        // 2) Ordena os cards:
        //    - prioridade (melhoria=1, incidente=2, requisição=3, default=4)
        //    - depois nome do dev
        cards = cards.stream()
                .sorted(
                        Comparator
                                .comparingInt((Card c) -> getPriority(getTituloFinal(c, isDevReport)))
                                .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                )
                .collect(Collectors.toList());

        // 3) Cria a planilha
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("devReport");

        // 4) Cabeçalho fixo
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Título");
        header.createCell(1).setCellValue("Desenvolvedor");
        header.createCell(2).setCellValue("Canal");
        header.createCell(3).setCellValue("Chamado");
        header.createCell(4).setCellValue("Horas Estipuladas");
        header.createCell(5).setCellValue("Progresso (%)");
        header.createCell(6).setCellValue("IN PROGRESS INTERVAL");

        // 5) Se NÃO for o cálculo semanal estipulado, criamos as colunas dinâmicas
        int dynamicStart = 7;
        if (!weeklyStipulatedCalculation) {
            for (int i = 0; i < sortedColumns.size(); i++) {
                header.createCell(dynamicStart + i).setCellValue(sortedColumns.get(i).name());
            }
        }

        // 6) Preenche as linhas
        int rowIndex = 1;
        for (Card card : cards) {
            Row row = sheet.createRow(rowIndex++);

            // 6.1) Dados básicos (isDevReport=true)
            String titulo = getTituloFinal(card, isDevReport);
            String dev = getDeveloperName(card.ownerUserId(), userMap);
            String chamado = card.customId();

            // Monta canal
            String canal = "";
            if (fillChannels) {
                List<Integer> tagIds = (card.tagIds() != null) ? card.tagIds() : List.of();
                canal = tagIds.stream()
                        .map(id -> tagMap.getOrDefault(id, ""))
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining(", "));
            }

            // Preenche as colunas fixas
            row.createCell(0).setCellValue(titulo);
            row.createCell(1).setCellValue(dev);
            row.createCell(2).setCellValue(canal);
            row.createCell(3).setCellValue(chamado);

            // Horas Estipuladas (coluna 4)
            String stipulatedHoursStr = getStipulatedHours(card);
            row.createCell(4).setCellValue(stipulatedHoursStr);

            double stipulatedHours = 0.0;
            if (stipulatedHoursStr != null && !stipulatedHoursStr.isEmpty()) {
                try {
                    // Permite tanto . quanto , como separador decimal
                    stipulatedHours = Double.parseDouble(stipulatedHoursStr.replace(",", "."));
                } catch (NumberFormatException e) {
                    // se der erro, fica em 0
                }
            }

            // 6.2) IN PROGRESS => ID=31
            List<LeadTimePerColumn> leadTimes = (card.leadTimePerColumn() != null)
                    ? card.leadTimePerColumn()
                    : List.of();
            Map<Long, Long> leadMap = leadTimes.stream()
                    .collect(Collectors.toMap(
                            lt -> (long) lt.columnId(),
                            LeadTimePerColumn::leadTime
                    ));

            // Tempo total que o Card ficou em IN PROGRESS
            long inProgressSeconds = leadMap.getOrDefault(31L, 0L);

            // 6.3) IN PROGRESS INTERVAL => tempo parcial no [from, to]
            long partialSeconds = intervalProgressService.getInProgressWithinPeriod(card, from, to);
            row.createCell(6).setCellValue(formatSeconds(partialSeconds));

            // Calcula "Progresso (%)" com base no parâmetro
            double estimatedSeconds = stipulatedHours * 3600.0;
            double progressPercentage = 0.0;

            if (weeklyStipulatedCalculation) {
                // Se weeklyStipulatedCalculation = true, usa partialSeconds
                if (estimatedSeconds > 0 && partialSeconds > 0) {
                    progressPercentage = (partialSeconds / estimatedSeconds) * 100.0;
                }
            } else {
                // Senão, usa o tempo total inProgressSeconds
                if (estimatedSeconds > 0 && inProgressSeconds > 0) {
                    progressPercentage = (inProgressSeconds / estimatedSeconds) * 100.0;
                }
            }

            String progressText = String.format("%.1f%%", progressPercentage);
            row.createCell(5).setCellValue(progressText);

            // 6.4) Se NÃO for weeklyStipulatedCalculation, preenche as colunas dinâmicas
            if (!weeklyStipulatedCalculation) {
                int colIndex = dynamicStart;
                for (Column col : sortedColumns) {
                    long cId = col.column_id();
                    long seconds = leadMap.getOrDefault(cId, 0L);

                    if (seconds > 0) {
                        row.createCell(colIndex).setCellValue(formatSeconds(seconds));
                    } else {
                        row.createCell(colIndex).setCellValue("");
                    }
                    colIndex++;
                }
            }
        }

        // 7) Salva o arquivo
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }



    /**
     * Converte segundos em "HHh MMm SSs"
     */
    private String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long remainder = totalSeconds % 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    // ---------------------------------------------------
    // Métodos auxiliares
    // ---------------------------------------------------

    /**
     * Ajusta o título de um Card. Se não encontrou o campo 13, só anexa "-PENDENTE"
     * quando não for devReport.
     */
    private String getTituloFinal(Card card, boolean isDevReport) {
        String titulo = card.title();
        boolean encontrouCampo13 = false;
        if (card.customFields() != null) {
            for (CustomField field : card.customFields()) {
                if (field.fieldId() == 13 && field.value() != null && !field.value().isEmpty()) {
                    titulo = field.value();
                    encontrouCampo13 = true;
                    break;
                }
            }
        }
        // Só adiciona " -PENDENTE" se não for devReport
        if (!encontrouCampo13 && !isDevReport) {
            titulo += " -PENDENTE";
        }
        return titulo;
    }

    /**
     * Método auxiliar usado em lugares onde ainda não passamos isDevReport explicitamente
     * (ex: sorting genérico). Aqui você poderia assumir que não é devReport, OU
     * simplesmente manter para retrocompatibilidade.
     */
    private String getTituloFinal(Card card) {
        return getTituloFinal(card, false);
    }

    private String getDeveloperName(int userId, Map<Long, User> userMap) {
        if (userMap == null) return "";
        User user = userMap.get((long) userId);
        return (user != null) ? user.realname() : "";
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

    private String getStipulatedHours(Card card) {
        if (card.customFields() == null) return "";
        for (CustomField cf : card.customFields()) {
            if (cf.fieldId() == 9 && cf.value() != null && !cf.value().isEmpty()) {
                return cf.value();
            }
        }
        return "";
    }
}
