package com.kanban_api.kanban_api.services;

import com.kanban_api.kanban_api.models.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    @Autowired
    private TagService tagService;

    private static final String OUTPUT_DIR = "output/";

    /**
     * Ordem preferencial das colunas:
     */
    private static final List<String> PREFERRED_ORDER = Arrays.asList(
            "BACKLOG",
            "TO DO",
            "IN PROGRESS",
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

            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetExcel(filepath, cards, userMap, tagMap, fillChannels, includePoints);
            } else {
                // Se não for singleSheet, gerar 1 arquivo por coluna
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    List<Card> cardsDaColuna = cardsByColumn.getOrDefault(colId, List.of());

                    String filePath = OUTPUT_DIR + fileBaseName + "-" + columnId + ".xlsx";
                    saveSingleSheetExcel(filePath, cardsDaColuna, userMap, tagMap, fillChannels, includePoints);
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
            boolean includePoints
    ) throws IOException {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Relatório");

        // Ordenação de exemplo (pode ajustar)
        cards = cards.stream()
                .sorted(
                        Comparator
                                .comparingInt((Card c) -> getPriority(getTituloFinal(c)))
                                .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                )
                .collect(Collectors.toList());

        // Cabeçalho fixo
        createHeader(sheet, includePoints);

        int rowIndex = 1;
        int totalPontos = 0;
        for (Card card : cards) {
            Row row = sheet.createRow(rowIndex++);

            String tituloFinal = getTituloFinal(card);
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
            List<Column> columns
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

            // Gera singleSheet ou multi-sheet
            if (singleSheet) {
                String filepath = OUTPUT_DIR + fileBaseName + ".xlsx";
                saveSingleSheetDevDynamic(filepath, cards, userMap, tagMap, fillChannels, columns);
            } else {
                Map<Integer, List<Card>> cardsByColumn = cards.stream()
                        .collect(Collectors.groupingBy(Card::columnId));

                for (String columnId : columnIds) {
                    int colId = Integer.parseInt(columnId);
                    List<Card> cardsDaColuna = cardsByColumn.getOrDefault(colId, List.of());

                    String filePath = OUTPUT_DIR + fileBaseName + "-" + columnId + ".xlsx";
                    saveSingleSheetDevDynamic(filePath, cardsDaColuna, userMap, tagMap, fillChannels, columns);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar Excel Dev Dynamic: " + e.getMessage(), e);
        }
    }

    /**
     * Cria uma aba "devReport" com colunas dinâmicas em ordem preferencial
     */
    private void saveSingleSheetDevDynamic(
            String filePath,
            List<Card> cards,
            Map<Long, User> userMap,
            Map<Integer, String> tagMap,
            boolean fillChannels,
            List<Column> columns
    ) throws IOException {

        // 1) Ordena "columns" pela lista preferida + alfabético
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
            // Nenhum dos dois na preferida -> ordenar por nome
            return n1.compareTo(n2);
        });

        // 2) Ordena os cards (exemplo)
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("devReport");

        cards = cards.stream()
                .sorted(
                        Comparator
                                .comparingInt((Card c) -> getPriority(getTituloFinal(c)))
                                .thenComparing((Card c) -> getDeveloperName(c.ownerUserId(), userMap))
                )
                .collect(Collectors.toList());

        // 3) Monta cabeçalho fixo
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Título");
        header.createCell(1).setCellValue("Desenvolvedor");
        header.createCell(2).setCellValue("Canal");
        header.createCell(3).setCellValue("Chamado");

        header.createCell(4).setCellValue("Horas Estipuladas");

        // 4) Cria colunas extras, seguindo "sortedColumns"
        int dynamicStart = 5;
        for (int i = 0; i < sortedColumns.size(); i++) {
            header.createCell(dynamicStart + i).setCellValue(sortedColumns.get(i).name());
        }

        // 5) Preenche linhas
        int rowIndex = 1;
        for (Card card : cards) {
            Row row = sheet.createRow(rowIndex++);

            String titulo = getTituloFinal(card);
            String dev = getDeveloperName(card.ownerUserId(), userMap);
            String chamado = card.customId();

            String canal = "";
            if (fillChannels) {
                List<Integer> cardTagIds = (card.tagIds() != null) ? card.tagIds() : List.of();
                canal = cardTagIds.stream()
                        .map(id -> tagMap.getOrDefault(id, ""))
                        .filter(lbl -> !lbl.isBlank())
                        .collect(Collectors.joining(", "));
            }

            // Preenche as 5 colunas fixas
            row.createCell(0).setCellValue(titulo);
            row.createCell(1).setCellValue(dev);
            row.createCell(2).setCellValue(canal);
            row.createCell(3).setCellValue(chamado);

            String stipulatedHours = getStipulatedHours(card);
            row.createCell(4).setCellValue(stipulatedHours);

            // Monta map columnId -> leadTime
            List<LeadTimePerColumn> leadTimes = (card.leadTimePerColumn() != null)
                    ? card.leadTimePerColumn()
                    : List.of();
            Map<Long, Long> leadMap = leadTimes.stream().collect(Collectors.toMap(
                            lt -> (long) lt.columnId(),
                            LeadTimePerColumn::leadTime
                    ));

            int colIndex = 5;
            for (Column col : sortedColumns) {
                long cId = col.column_id();
                Long seconds = leadMap.getOrDefault(cId, 0L);
                if (seconds > 0) {
                    row.createCell(colIndex).setCellValue(formatSeconds(seconds));
                } else {
                    // caso não tenha lead time nessa coluna
                    row.createCell(colIndex).setCellValue("");
                }
                colIndex++;
            }
        }

        // 6) Salva e fecha
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
    // Métodos auxiliares (iguais aos anteriores)
    // ---------------------------------------------------
    private String getTituloFinal(Card card) {
        String titulo = card.title();
        boolean encontrouCampo13 = false;
        for (CustomField field : card.customFields()) {
            if (field.fieldId() == 13 && field.value() != null && !field.value().isEmpty()) {
                titulo = field.value();
                encontrouCampo13 = true;
                break;
            }
        }
        if (!encontrouCampo13) {
            titulo += " -PENDENTE";
        }
        return titulo;
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
            if (cf.fieldId() == 22 && cf.value() != null && !cf.value().isEmpty()) {
                return cf.value();
            }
        }
        return "";
    }

}