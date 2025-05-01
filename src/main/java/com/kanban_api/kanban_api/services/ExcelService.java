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

    // >>> NOVAS CONSTANTES para as colunas DONE, CLIENT_DEMO e DEPLOYED <<<
    private static final long DONE_COLUMN_ID = 32L;
    private static final long CLIENT_DEMO_COLUMN_ID = 163L;
    private static final long DEPLOYED_COLUMN_ID = 164L;

    private static final String OUTPUT_DIR = "output/";

    // Ordem preferencial das colunas no devReport.
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

    @Autowired
    private DevIndividualReportService devIndividualReportService;

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

            Map<Long, User> userMap = allUsers.stream()
                    .collect(Collectors.toMap(User::user_id, u -> u));

            List<Tag> allTags = tagService.getAllTags();
            Map<Integer, String> tagMap = allTags.stream()
                    .collect(Collectors.toMap(Tag::tag_id, Tag::label));

            boolean isDevReport = false;

            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetExcel(filepath, cards, userMap, tagMap, fillChannels, includePoints, isDevReport, deployTimes);
            } else {
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

        // Ordenação
        cards = cards.stream()
                .sorted(
                        Comparator
                                .comparingInt((Card c) -> getPriority(determineFinalTitle(c, isDevReport)))
                                .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                )
                .collect(Collectors.toList());

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

            // Pontos
            int points = 0;
            if (includePoints) {
                points = calculatePoints(finalTitle);
                totalPoints += points;
            }

            row.createCell(0).setCellValue(finalTitle);
            row.createCell(1).setCellValue(devName);
            row.createCell(2).setCellValue(channel);
            row.createCell(3).setCellValue(customId);

            if (includePoints) {
                row.createCell(4).setCellValue(points);
            }

            if (includeDeployTime) {
                LocalDateTime deployTime = deployTimes.get(Long.valueOf(card.cardId()));
                row.createCell(5).setCellValue(
                        (deployTime != null) ? deployTime.toString() : ""
                );
            }
        }

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
    // 2) devReport dinâmico
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
            boolean weeklyStipulatedCalculation,
            boolean resultsByDev,
            double legendaryThreshold
    ) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            Map<Long, User> userMap = allUsers.stream()
                    .collect(Collectors.toMap(User::user_id, u -> u));

            List<Tag> allTags = tagService.getAllTags();
            Map<Integer, String> tagMap = allTags.stream()
                    .collect(Collectors.toMap(Tag::tag_id, Tag::label));

            boolean isDevReport = true;

            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetDevDynamic(
                        filepath,
                        cards,
                        userMap,
                        tagMap,
                        fillChannels,
                        columns,
                        from,
                        to,
                        isDevReport,
                        weeklyStipulatedCalculation,
                        resultsByDev,
                        allUsers,
                        legendaryThreshold
                );
            } else {
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    List<Card> cardsFromColumn = cardsByColumn.getOrDefault(colId, List.of());

                    String filePath = OUTPUT_DIR + fileBaseName + "-" + columnId + ".xlsx";
                    saveSingleSheetDevDynamic(
                            filePath,
                            cardsFromColumn,
                            userMap,
                            tagMap,
                            fillChannels,
                            columns,
                            from,
                            to,
                            isDevReport,
                            weeklyStipulatedCalculation,
                            resultsByDev,
                            allUsers,
                            legendaryThreshold
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error saving Excel for dev dynamic report: " + e.getMessage(), e);
        }
    }

    /**
     * Salva a planilha devReport
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
            boolean weeklyStipulatedCalculation,
            boolean resultsByDev,
            List<User> allUsers,
            double legendaryThreshold
    ) throws IOException {

        Workbook workbook = new XSSFWorkbook();

        CellStyle redTextStyle = createColoredStyle(workbook, IndexedColors.RED, false);
        CellStyle orangeTextStyle = createColoredStyle(workbook, IndexedColors.ORANGE, false);
        CellStyle greenTextStyle = createColoredStyle(workbook, IndexedColors.GREEN, false);

        CellStyle blueTextStyle = createColoredStyle(workbook, IndexedColors.BLUE, false);
        // Verde + negrito (LENDÁRIO!)
        CellStyle greenBoldStyle = createColoredStyle(workbook, IndexedColors.GREEN, true);

        // Ordena colunas pela ordem preferencial
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

        Sheet sheet = workbook.createSheet("devReport");

        if (weeklyStipulatedCalculation) {
            // -------------------------------------------------------------------------
            // 1) Cabeçalho (renomeando para "Assertividade (%)" no lugar de "Progresso")
            // -------------------------------------------------------------------------
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Chamado");
            header.createCell(1).setCellValue("Título");
            header.createCell(2).setCellValue("Desenvolvedor");
            header.createCell(3).setCellValue("Horas Estipuladas");
            header.createCell(4).setCellValue("In Progress Interval");
            header.createCell(5).setCellValue("Assertividade (%)"); // renomeado
            header.createCell(6).setCellValue("Status");

            // -------------------------------------------------------------------------
            // 2) Guardar os dados em uma lista auxiliar, inclusive se é LENDÁRIO ou não
            // -------------------------------------------------------------------------
            List<CardAux> cardAuxList = new ArrayList<>();
            for (Card card : cards) {
                double stipulatedHours = parseStipulatedHours(getStipulatedHoursValue(card));
                double estimatedSeconds = stipulatedHours * 3600.0;
                long partialSeconds = intervalProgressService.getInProgressWithinPeriod(card, from, to);

                double partialRatio = 0.0;
                if (estimatedSeconds > 0 && partialSeconds > 0) {
                    partialRatio = partialSeconds / estimatedSeconds * 100.0;
                }

                boolean isDoneOrDeployedOrDemo = isFinalColumnOneOf(
                        card.columnId(),
                        DONE_COLUMN_ID,
                        DEPLOYED_COLUMN_ID,
                        CLIENT_DEMO_COLUMN_ID
                );

                boolean isLegendary = false;
                // Só se legendaryThreshold <= 100
                if (legendaryThreshold <= 100
                        && isDoneOrDeployedOrDemo
                        && partialSeconds < estimatedSeconds
                        && partialRatio >= legendaryThreshold)
                {
                    isLegendary = true;
                }

                // Monta o CardAux
                CardAux aux = new CardAux(card, partialSeconds, partialRatio);
                aux.stipulatedHours = stipulatedHours;
                aux.isLegendary = isLegendary;
                aux.estimatedSeconds = estimatedSeconds;

                cardAuxList.add(aux);
            }

            // -------------------------------------------------------------------------
            // 3) Ordenar: lendários primeiro, depois o restante, cada grupo pela "proximidade de 100"
            // -------------------------------------------------------------------------
            cardAuxList.sort((a, b) -> {
                // Lendários primeiro
                if (a.isLegendary && !b.isLegendary) return -1;
                if (!a.isLegendary && b.isLegendary) return 1;

                // Se ambos lendários ou ambos não-lendários, comparar pela dist. de 100
                double distA = Math.abs(a.progressPercentage - 100.0);
                double distB = Math.abs(b.progressPercentage - 100.0);
                return Double.compare(distA, distB);
            });

            // -------------------------------------------------------------------------
            // 4) Imprimir as linhas no Excel, usando a lista final
            // -------------------------------------------------------------------------
            int rowIndex = 1;
            for (CardAux aux : cardAuxList) {
                Card card = aux.card;
                Row row = sheet.createRow(rowIndex++);

                // Horas / partialSeconds / partialRatio
                double stipulatedHours = aux.stipulatedHours;
                double partialSeconds = aux.partialSeconds;
                double progressPercentage = aux.progressPercentage; // 0 a 100+ ?

                // Se stipulatedHours==0 => "-"
                String hoursCellValue = (stipulatedHours > 0)
                        ? String.valueOf(stipulatedHours)
                        : "-";

                // Se hours==0 => assertividade = "-"
                String assertividadeValue = (stipulatedHours == 0)
                        ? "-"
                        : String.format("%.1f%%", progressPercentage);

                String customId = card.customId();
                String finalTitle = determineFinalTitle(card, isDevReport);
                String devName = getDeveloperName(card.ownerUserId(), userMap);

                // Preenche colunas
                row.createCell(0).setCellValue(customId);
                row.createCell(1).setCellValue(finalTitle);
                row.createCell(2).setCellValue(devName);
                row.createCell(3).setCellValue(hoursCellValue);
                row.createCell(4).setCellValue(formatSeconds((long) partialSeconds));
                row.createCell(5).setCellValue(assertividadeValue);

                // Determina status
                Cell statusCell = row.createCell(6);

                if (stipulatedHours == 0) {
                    // SEM PLANEJAMENTO
                    statusCell.setCellValue("SEM PLANEJAMENTO");
                    statusCell.setCellStyle(blueTextStyle);
                    continue;
                }

                // Se isLegendary == true => "LENDÁRIO!"
                if (aux.isLegendary) {
                    statusCell.setCellValue("LENDÁRIO!");
                    statusCell.setCellStyle(greenBoldStyle);
                    continue;
                }

                // Caso contrário, lógica GAME OVER, QUE TAL, JOGOU BEM
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

            // -------------------------------------------------------------------------
            // 5) Legenda etc.
            // -------------------------------------------------------------------------
            int legendRowIndex = cardAuxList.size() + 3;
            Row legendRow1 = sheet.createRow(legendRowIndex++);
            legendRow1.createCell(0).setCellValue("Legenda do Cálculo de Progresso Semanal:");

            Row legendRow2 = sheet.createRow(legendRowIndex++);
            legendRow2.createCell(0).setCellValue("• IN PROGRESS INTERVAL = Tempo que o card ficou na coluna 'IN PROGRESS' durante a semana.");

            Row legendRow3 = sheet.createRow(legendRowIndex++);
            legendRow3.createCell(0).setCellValue("• Assertividade (%) = (In Progress Interval / Horas Estipuladas) x 100.");

            Row legendRow4 = sheet.createRow(legendRowIndex++);
            legendRow4.createCell(0).setCellValue("• Quanto mais próximo de 100%, mais acertada a estimativa.");

            Row legendRow5 = sheet.createRow(legendRowIndex++);
            legendRow5.createCell(0).setCellValue("• GAME OVER (<75% ou >125%) / QUE TAL DA PRÓXIMA ? (>=75% e <95% ou >105% e <=125%) / JOGOU BEM!!! (>=95% e <=105%).");

            Row legendRow6 = sheet.createRow(legendRowIndex++);
            legendRow6.createCell(0).setCellValue("• Neste relatório, constam apenas os cards que passaram pela coluna 'IN PROGRESS' e possuíam 'HORAS ESTIPULADAS'.");

            if (resultsByDev) {
                int startRowForDevs = legendRowIndex + 2;
                // Anexa o bloco individual
                devIndividualReportService.appendByDevBelow(sheet, cards, from, to, allUsers, startRowForDevs);
            }

        } else {
            // Cabeçalho completo...
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Título");
            header.createCell(1).setCellValue("Desenvolvedor");
            header.createCell(2).setCellValue("Canal");
            header.createCell(3).setCellValue("Chamado");
            header.createCell(4).setCellValue("Horas Estipuladas");
            header.createCell(5).setCellValue("Assertividade (%)"); // renomeamos aqui também
            header.createCell(6).setCellValue("In Progress Interval");

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

                double stipulatedHours = parseStipulatedHours(getStipulatedHoursValue(card));
                double estimatedSeconds = stipulatedHours * 3600.0;

                List<LeadTimePerColumn> leadTimes = (card.leadTimePerColumn() != null)
                        ? card.leadTimePerColumn()
                        : List.of();

                Map<Long, Long> leadMap = leadTimes.stream()
                        .collect(Collectors.toMap(
                                lt -> (long) lt.columnId(),
                                LeadTimePerColumn::leadTime
                        ));

                long partialSeconds = intervalProgressService.getInProgressWithinPeriod(card, from, to);

                double progressPercentage = 0.0;
                if (estimatedSeconds > 0 && partialSeconds > 0) {
                    progressPercentage = partialSeconds / estimatedSeconds * 100.0;
                }
                String progressText = String.format("%.1f%%", progressPercentage);

                // Preenche células
                row.createCell(0).setCellValue(finalTitle);
                row.createCell(1).setCellValue(devName);
                row.createCell(2).setCellValue(channel);
                row.createCell(3).setCellValue(customId);
                row.createCell(4).setCellValue(
                        (stipulatedHours > 0)
                                ? String.valueOf(stipulatedHours)
                                : "-"
                );
                row.createCell(5).setCellValue(
                        (stipulatedHours == 0)
                                ? "-"
                                : progressText
                );
                row.createCell(6).setCellValue(formatSeconds(partialSeconds));

                // As colunas dinâmicas
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

            if (resultsByDev) {
                int startRowForDevs = cards.size() + 3;
                devIndividualReportService.appendByDevBelow(sheet, cards, from, to, allUsers, startRowForDevs);
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

    // >>> Novo método para criar estilo com cor e, opcionalmente, negrito <<<
    private CellStyle createColoredStyle(Workbook workbook, IndexedColors color, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(color.getIndex());
        if (bold) {
            font.setBold(true);
        }
        style.setFont(font);
        return style;
    }

    /**
     * Verifica se o card está em uma das colunas [done, deployed, client demo].
     */
    private boolean isFinalColumnOneOf(long cardColumnId, long c1, long c2, long c3) {
        return cardColumnId == c1 || cardColumnId == c2 || cardColumnId == c3;
    }

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
     * Classe auxiliar para armazenar o card + métricas calculadas,
     * inclusive se é lendário ou não.
     */
    private static class CardAux {
        Card card;
        long partialSeconds;
        double progressPercentage; // ex.: 80.0 => 80%
        boolean isLegendary;
        double stipulatedHours;
        double estimatedSeconds;

        CardAux(Card card, long partialSeconds, double progressPercentage) {
            this.card = card;
            this.partialSeconds = partialSeconds;
            this.progressPercentage = progressPercentage;
            this.isLegendary = false;
            this.stipulatedHours = 0.0;
            this.estimatedSeconds = 0.0;
        }
    }
}