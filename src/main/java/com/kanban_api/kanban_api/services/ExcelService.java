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

/**
 * Responsável por gerar arquivos Excel (relatórios semanais e devReports).
 */
@Service
public class ExcelService {

    private static final int TITLE_FIELD_ID = 13;
    private static final int STIPULATED_HOURS_ID = 9;
    private static final Long IN_PROGRESS_COLUMN = 31L;
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

    @Autowired
    private TagService tagService;

    @Autowired
    private IntervalProgressService intervalProgressService;

    private static final String OUTPUT_DIR = "output/";

    /**
     * Ordem preferencial das colunas no devReport.
     */

    // ------------------------------------------------------------------------------------
    // 1) Relatório SEMANAL (com pontos)
    // ------------------------------------------------------------------------------------
    public void saveToExcel(
            List<Card> cards,
            boolean singleSheet,
            List<String> columnIds,
            List<User> allUsers,
            boolean fillChannels,
            boolean includePoints,
            String fileBaseName,
            Map<Long, LocalDateTime> deployTimes
    ) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Mapeia userId -> User
            Map<Long, User> userMap = allUsers.stream()
                    .collect(Collectors.toMap(User::user_id, u -> u));

            // Mapeia tagId -> label
            List<Tag> allTags = tagService.getAllTags();
            Map<Integer, String> tagMap = allTags.stream()
                    .collect(Collectors.toMap(Tag::tag_id, Tag::label));

            // isDevReport = false (pois é relatório semanal)
            boolean isDevReport = false;

            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetExcel(filepath, cards, userMap, tagMap, fillChannels, includePoints, isDevReport, deployTimes);
            } else {
                // Gerar 1 arquivo por coluna
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    List<Card> cardsFromColumn = cardsByColumn.getOrDefault(colId, List.of());

                    String filePath = OUTPUT_DIR + fileBaseName + "-" + columnId + ".xlsx";
                    saveSingleSheetExcel(filePath, cardsFromColumn, userMap, tagMap, fillChannels, includePoints, isDevReport, deployTimes);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error saving Excel for weekly report: " + e.getMessage(), e);
        }
    }

    /**
     * Cria apenas UMA aba Excel para o relatório semanal.
     */
    private void saveSingleSheetExcel(
            String filePath,
            List<Card> cards,
            Map<Long, User> userMap,
            Map<Integer, String> tagMap,
            boolean fillChannels,
            boolean includePoints,
            boolean isDevReport,
            Map<Long, LocalDateTime> deployTimes
    ) throws IOException {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Report");

        // Ordenação básica
        cards = cards.stream()
                .sorted(
                        Comparator
                                .comparingInt((Card c) -> getPriority(determineFinalTitle(c, isDevReport)))
                                .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                )
                .collect(Collectors.toList());

        // Cabeçalho
        boolean includeDeployTime = deployTimes != null;
        createWeeklyHeader(sheet, includePoints, includeDeployTime);

        int rowIndex = 1;
        int totalPoints = 0;
        for (Card card : cards) {
            Row row = sheet.createRow(rowIndex++);

            String finalTitle = determineFinalTitle(card, isDevReport);
            String devName = getDeveloperName(card.ownerUserId(), userMap);
            String customId = card.customId();

            // Canal
            String channel = "";
            if (fillChannels) {
                List<Integer> tagIds = (card.tagIds() != null) ? card.tagIds() : List.of();
                channel = tagIds.stream()
                        .map(id -> tagMap.getOrDefault(id, ""))
                        .filter(lbl -> !lbl.isBlank())
                        .collect(Collectors.joining(", "));
            }

            // Pontos (opcional)
            int points = 0;
            if (includePoints) {
                points = calculatePoints(finalTitle);
                totalPoints += points;
            }

            // Preenche células
            row.createCell(0).setCellValue(finalTitle);
            row.createCell(1).setCellValue(devName);
            row.createCell(2).setCellValue(channel);
            row.createCell(3).setCellValue(customId);

            if (includePoints) {
                row.createCell(4).setCellValue(points);
            }

            // Coluna "Hora do Deploy" (Opcional)
            if (includeDeployTime) {
                LocalDateTime deployTime = deployTimes.get(Long.valueOf(card.cardId()));
                if (deployTime != null) {
                    // Formate do seu jeito - por exemplo, "yyyy-MM-dd HH:mm"
                    // Abaixo apenas .toString() para simplificar
                    row.createCell(5).setCellValue(deployTime.toString());
                } else {
                    row.createCell(5).setCellValue("");
                }
            }
        }

        // Desempenho (cálculo simples)
        if (includePoints && !cards.isEmpty()) {
            double performance = (double) totalPoints / (cards.size() * 20) * 100;
            Row finalRow = sheet.createRow(cards.size() + 2);
            finalRow.createCell(0)
                    .setCellValue("Desempenho por entrega: " + String.format("%.2f", performance) + "%");
        }

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createWeeklyHeader(Sheet sheet, boolean includePoints, boolean includeDeployTime) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Título");
        headerRow.createCell(1).setCellValue("Desenvolvedor");
        headerRow.createCell(2).setCellValue("Canal");
        headerRow.createCell(3).setCellValue("Chamado");
        if (includePoints) {
            headerRow.createCell(4).setCellValue("Pontos");
        }
        if (includeDeployTime) {
            headerRow.createCell(5).setCellValue("Hora do Deploy");
        }
    }

    // ------------------------------------------------------------------------------------
    // 2) devReport dinâmico (usa lead_time_per_column e columns) com ordem custom
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

            boolean isDevReport = true;

            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetDevDynamic(filepath, cards, userMap, tagMap, fillChannels, columns, from, to, isDevReport, weeklyStipulatedCalculation);
            } else {
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    List<Card> cardsFromColumn = cardsByColumn.getOrDefault(colId, List.of());

                    String filePath = OUTPUT_DIR + fileBaseName + "-" + columnId + ".xlsx";
                    saveSingleSheetDevDynamic(filePath, cardsFromColumn, userMap, tagMap, fillChannels, columns, from, to, isDevReport, weeklyStipulatedCalculation);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error saving Excel for dev dynamic report: " + e.getMessage(), e);
        }
    }

    /**
     * Cria uma aba "devReport" com colunas dinâmicas em ordem preferencial.
     * Se weeklyStipulatedCalculation=true, a lógica de ordenação muda um pouco.
     */
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

        Workbook workbook = new XSSFWorkbook();

        // Styles para a coluna "Status"
        CellStyle redTextStyle = createTextColorStyle(workbook, IndexedColors.RED);
        CellStyle orangeTextStyle = createTextColorStyle(workbook, IndexedColors.ORANGE);
        CellStyle greenTextStyle = createTextColorStyle(workbook, IndexedColors.GREEN);

        // Ordena colunas pela ordem preferencial + alfabético se não tiver na lista
        List<Column> sortedColumns = new ArrayList<>(columns);
        sortedColumns.sort((c1, c2) -> {
            String n1 = c1.name().toUpperCase();
            String n2 = c2.name().toUpperCase();

            int idx1 = PREFERRED_ORDER.indexOf(n1);
            int idx2 = PREFERRED_ORDER.indexOf(n2);

            if (idx1 >= 0 && idx2 >= 0) {
                return Integer.compare(idx1, idx2);
            }
            if (idx1 >= 0) {
                return -1;
            }
            if (idx2 >= 0) {
                return 1;
            }
            return n1.compareTo(n2);
        });

        // Ordenação dos cards
        if (weeklyStipulatedCalculation) {
            // Ordenar pela "proximidade" de 100%
            List<CardAux> cardAuxList = new ArrayList<>();
            for (Card card : cards) {
                double stipulatedHours = parseStipulatedHours(getStipulatedHoursValue(card));
                double estimatedSeconds = stipulatedHours * 3600.0;
                long partialSeconds = intervalProgressService.getInProgressWithinPeriod(card, from, to);

                double progressPercentage = 0.0;
                if (estimatedSeconds > 0 && partialSeconds > 0) {
                    progressPercentage = partialSeconds / estimatedSeconds * 100.0;
                }
                cardAuxList.add(new CardAux(card, partialSeconds, progressPercentage));
            }
            cardAuxList.sort(Comparator.comparingDouble(aux -> Math.abs(aux.progressPercentage - 100.0)));
            // Reconstrói a lista
            cards = cardAuxList.stream().map(aux -> aux.card).collect(Collectors.toList());

        } else {
            // Ordenação antiga: prioridade + dev
            cards = cards.stream()
                    .sorted(
                            Comparator
                                    .comparingInt((Card c) -> getPriority(determineFinalTitle(c, isDevReport)))
                                    .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                    )
                    .collect(Collectors.toList());
        }

        Sheet sheet = workbook.createSheet("devReport");

        if (weeklyStipulatedCalculation) {
            // Cabeçalho específico
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Chamado");
            header.createCell(1).setCellValue("Título");
            header.createCell(2).setCellValue("Desenvolvedor");
            header.createCell(3).setCellValue("Horas Estipuladas");
            header.createCell(4).setCellValue("In Progress Interval");
            header.createCell(5).setCellValue("Progresso (%)");
            header.createCell(6).setCellValue("Status");

            int rowIndex = 1;
            for (Card card : cards) {
                Row row = sheet.createRow(rowIndex++);

                String customId = card.customId();
                String finalTitle = determineFinalTitle(card, isDevReport);
                String devName = getDeveloperName(card.ownerUserId(), userMap);

                double stipulatedHours = parseStipulatedHours(getStipulatedHoursValue(card));
                double estimatedSeconds = stipulatedHours * 3600.0;

                long partialSeconds = intervalProgressService.getInProgressWithinPeriod(card, from, to);
                double progressPercentage = 0.0;
                if (estimatedSeconds > 0 && partialSeconds > 0) {
                    progressPercentage = partialSeconds / estimatedSeconds * 100.0;
                }

                row.createCell(0).setCellValue(customId);
                row.createCell(1).setCellValue(finalTitle);
                row.createCell(2).setCellValue(devName);
                row.createCell(3).setCellValue(String.valueOf(stipulatedHours));
                row.createCell(4).setCellValue(formatSeconds(partialSeconds));
                row.createCell(5).setCellValue(String.format("%.1f%%", progressPercentage));

                // Status com cor
                Cell statusCell = row.createCell(6);
                if (progressPercentage < 75.0 || progressPercentage > 125.0) {
                    statusCell.setCellValue("GAME OVER");
                    statusCell.setCellStyle(redTextStyle);
                } else if ((progressPercentage >= 75.0 && progressPercentage < 95.0)
                        || (progressPercentage > 105.0 && progressPercentage <= 125.0)) {
                    statusCell.setCellValue("QUE TAL DA PRÓXIMA ?");
                    statusCell.setCellStyle(orangeTextStyle);
                } else {
                    statusCell.setCellValue("JOGOU BEM!!!");
                    statusCell.setCellStyle(greenTextStyle);
                }
            }

            // Legenda
            int legendRowIndex = cards.size() + 3;
            Row legendRow1 = sheet.createRow(legendRowIndex++);
            legendRow1.createCell(0).setCellValue("Legenda do Cálculo de Progresso Semanal:");

            Row legendRow2 = sheet.createRow(legendRowIndex++);
            legendRow2.createCell(0).setCellValue(
                    "• IN PROGRESS INTERVAL = Tempo que o card ficou na coluna “IN PROGRESS” durante a semana."
            );

            Row legendRow3 = sheet.createRow(legendRowIndex++);
            legendRow3.createCell(0).setCellValue(
                    "• Progresso (%) = (In Progress Interval / Stipulated Hours) x 100."
            );

            Row legendRow4 = sheet.createRow(legendRowIndex++);
            legendRow4.createCell(0).setCellValue(
                    "• Quanto mais próximo de 100%, mais acertada a estimativa."
            );

            Row legendRow5 = sheet.createRow(legendRowIndex++);
            legendRow5.createCell(0).setCellValue(
                    "• GAME OVER (<75% ou >125%) / QUASE LÁ! (>=75% e <95% ou >105% e <=125%) / PARABÉNS!!! (>=95% e <=105%)."
            );

        } else {
            // Cabeçalho completo
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Título");
            header.createCell(1).setCellValue("Desenvolvedor");
            header.createCell(2).setCellValue("Canal");
            header.createCell(3).setCellValue("Chamado");
            header.createCell(4).setCellValue("Horas Estipuladas");
            header.createCell(5).setCellValue("Progresso (%)");
            header.createCell(6).setCellValue("In Progress Interval");

            // Colunas dinâmicas a partir de colIndex=7
            int dynamicStart = 7;
            for (int i = 0; i < sortedColumns.size(); i++) {
                header.createCell(dynamicStart + i).setCellValue(sortedColumns.get(i).name());
            }

            int rowIndex = 1;
            for (Card card : cards) {
                Row row = sheet.createRow(rowIndex++);

                String finalTitle = determineFinalTitle(card, isDevReport);
                String devName = getDeveloperName(card.ownerUserId(), userMap);

                // Canal
                String channel = "";
                if (fillChannels) {
                    List<Integer> tagIds = (card.tagIds() != null) ? card.tagIds() : List.of();
                    channel = tagIds.stream()
                            .map(id -> tagMap.getOrDefault(id, ""))
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.joining(", "));
                }
                String customId = card.customId();

                // Stipulated Hours
                String stipulatedHoursStr = getStipulatedHoursValue(card);
                double stipulatedHours = parseStipulatedHours(stipulatedHoursStr);
                double estimatedSeconds = stipulatedHours * 3600.0;

                // leadTimes
                List<LeadTimePerColumn> leadTimes = (card.leadTimePerColumn() != null)
                        ? card.leadTimePerColumn()
                        : List.of();

                Map<Long, Long> leadMap = leadTimes.stream()
                        .collect(Collectors.toMap(
                                lt -> (long) lt.columnId(),
                                LeadTimePerColumn::leadTime
                        ));

                long inProgressSeconds = leadMap.getOrDefault(IN_PROGRESS_COLUMN, 0L);
                long partialSeconds = intervalProgressService.getInProgressWithinPeriod(card, from, to);

                // Progresso
                double progressPercentage = 0.0;
                if (estimatedSeconds > 0 && inProgressSeconds > 0) {
                    progressPercentage = inProgressSeconds / estimatedSeconds * 100.0;
                }
                String progressText = String.format("%.1f%%", progressPercentage);

                // Preenche células
                row.createCell(0).setCellValue(finalTitle);
                row.createCell(1).setCellValue(devName);
                row.createCell(2).setCellValue(channel);
                row.createCell(3).setCellValue(customId);
                row.createCell(4).setCellValue(stipulatedHoursStr);
                row.createCell(5).setCellValue(progressText);
                row.createCell(6).setCellValue(formatSeconds(partialSeconds));

                // Colunas dinâmicas (lead_time_per_column)
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

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    // ---------------------------------------------------
    // Métodos auxiliares
    // ---------------------------------------------------

    /**
     * Formata segundos em "HHh MMm SSs".
     */
    private String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long remainder = totalSeconds % 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

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
     * Cria um CellStyle que apenas muda a cor da fonte (texto).
     */
    private CellStyle createTextColorStyle(Workbook workbook, IndexedColors fontColor) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(fontColor.getIndex());
        style.setFont(font);
        style.setFillPattern(FillPatternType.NO_FILL);
        return style;
    }

    /**
     * Usa o campo custom (ID=13) ou anexa "-PENDENTE" se não encontrou (caso não seja devReport).
     */
    private String determineFinalTitle(Card card, boolean isDevReport) {
        String originalTitle = card.title();
        boolean foundTitleField = false;

        if (card.customFields() != null) {
            for (CustomField field : card.customFields()) {
                if (field.fieldId() == TITLE_FIELD_ID && field.value() != null && !field.value().isEmpty()) {
                    originalTitle = field.value();
                    foundTitleField = true;
                    break;
                }
            }
        }

        if (!foundTitleField && !isDevReport) {
            // Só adiciona " -PENDENTE" se não for devReport
            originalTitle += " -PENDENTE";
        }
        return originalTitle;
    }

    private String getDeveloperName(int userId, Map<Long, User> userMap) {
        if (userMap == null) return "";
        User user = userMap.get((long) userId);
        return (user != null) ? user.realname() : "";
    }

    private int getPriority(String title) {
        String upper = title.toUpperCase();
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

    private int calculatePoints(String title) {
        String upper = title.toUpperCase();
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

    /**
     * Retorna valor do campo custom (ID=9) "Stipulated Hours", se existir.
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
     * Auxiliar para armazenar dados durante ordenação no weeklyStipulatedCalculation=true.
     */
    private static class CardAux {
        Card card;
        long partialSeconds;
        double progressPercentage;

        CardAux(Card card, long partialSeconds, double progressPercentage) {
            this.card = card;
            this.partialSeconds = partialSeconds;
            this.progressPercentage = progressPercentage;
        }
    }
}