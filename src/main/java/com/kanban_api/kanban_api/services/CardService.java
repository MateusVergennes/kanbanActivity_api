package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.*;
import com.kanban_api.kanban_api.views.CardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CardService {

    @Autowired
    private KanbanConfig kanbanConfig;

    @Autowired
    private CardView cardView;

    @Autowired
    private ExcelService excelService;

    @Autowired
    private UserService userService;

    @Autowired
    private ColumnService columnService;


    // ============================================================
    // 1) WEEKLY REPORT (relatório antigo, inclui pontos)
    // ============================================================
    public List<Card> getWeeklyReport(
            String startDate,
            String endDate,
            String columnIds,
            boolean singleSheet,
            boolean filterGithub,
            boolean fillChannels
    ) {
        try {
            // Ajusta datas
            if (startDate == null || startDate.isEmpty()) {
                startDate = LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE);
            }
            if (endDate == null || endDate.isEmpty()) {
                endDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            }

            // Converte string de colunas para lista
            List<String> columnList = Arrays.asList(columnIds.split(","));

            // Busca cards
            List<Card> allCards = fetchAllCards(startDate, endDate, columnList, filterGithub, singleSheet, false);

            // Salva JSON (por exemplo, "weekly-results-kanban.json")
            cardView.saveResults(allCards, "weekly-results-kanban.json");

            // Pega lista de usuários (mapeamento userId -> realname)
            UserResponse userResponse = userService.fetchUsers();
            List<User> allUsers = userResponse.data();

            // Gera EXCEL - definindo includePoints=true, e baseName="weekly-report"
            excelService.saveToExcel(
                    allCards,
                    singleSheet,
                    columnList,
                    allUsers,
                    fillChannels,
                    true,               // includePoints = true
                    "weekly-report"     // nome do arquivo base
            );

            return allCards;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao obter o relatório semanal: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // 2) DEV REPORT (novo, sem pontos)
    // ============================================================
    public List<Card> getDevReport(
            String startDate,
            String endDate,
            String columnIds,       // padrão será "30"
            boolean singleSheet,
            boolean filterGithub,
            boolean fillChannels
    ) {
        try {
            // Ajusta datas
            if (startDate == null || startDate.isEmpty()) {
                startDate = LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE);
            }
            if (endDate == null || endDate.isEmpty()) {
                endDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            }

            // Converte string de colunas para lista
            List<String> columnList = Arrays.asList(columnIds.split(","));

            // Busca cards
            List<Card> allCards = fetchAllCards(startDate, endDate, columnList, filterGithub, singleSheet, true);

            // Filtrar localmente: manter apenas os que passaram pela coluna 31 dentro do período
            LocalDateTime from = LocalDate.parse(startDate).atStartOfDay();
            // Fechamos em 23h59 do endDate
            LocalDateTime to = LocalDate.parse(endDate).atTime(23, 59, 59);

            allCards = allCards.stream()
                    .filter(card -> passedByTheColumnInPeriod(card, 31, from, to))
                    .collect(Collectors.toList());

            // Salva JSON (por exemplo, "dev-results-kanban.json")
            cardView.saveResults(allCards, "dev-results-kanban.json");

            // Pega lista de usuários
            UserResponse userResponse = userService.fetchUsers();
            List<User> allUsers = userResponse.data();

            // Carrega colunas do board=4, workflow=6 (mude se precisar)
            List<Column> columns = columnService.getColumns(4, 6L);

            // Gera Excel com colunas dinâmicas
            excelService.saveToExcelDevDynamic(
                    allCards,
                    singleSheet,
                    columnList,
                    allUsers,
                    fillChannels,
                    "dev-report",
                    columns
            );

            return allCards;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao obter o relatório DEV: " + e.getMessage(), e);
        }
    }


    // ============================================================
    // Método interno para buscar todos os cards de várias colunas
    // ============================================================
    private List<Card> fetchAllCards(
            String startDate,
            String endDate,
            List<String> columnList,
            boolean filterGithub,
            boolean singleSheet,
            boolean includeLeadTime
    ) {
        List<Card> allCards = new ArrayList<>();

        if (singleSheet) {
            // Faz apenas 1 requisição, unindo todos os column_ids em uma string
            String combinedCols = String.join(",", columnList);
            allCards = fetchCards(startDate, endDate, combinedCols, filterGithub, includeLeadTime);
        } else {
            // Faz uma requisição por coluna
            for (String col : columnList) {
                List<Card> cards = fetchCards(startDate, endDate, col, filterGithub, includeLeadTime);
                allCards.addAll(cards);
            }
        }
        return allCards;
    }

    /**
     * Busca cards de uma ou mais colunas (separadas por vírgula).
     */
    private List<Card> fetchCards(String startDate, String endDate, String columnId, boolean filterGithub, boolean includeLeadTime) {
        try {
            String expandParam = includeLeadTime
                    ? "custom_fields,tag_ids,lead_time_per_column,transitions"
                    : "custom_fields,tag_ids";

            String url = String.format(
                    "%s/cards?last_modified_from_date=%s&last_modified_to_date=%s&column_ids=%s&expand=%s",
                    kanbanConfig.getApiUrl(), startDate, endDate, columnId, expandParam
            );

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", kanbanConfig.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            ObjectMapper mapper = new ObjectMapper();
            mapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode cardsData = root.path("data").path("data");

            List<Card> cards = Arrays.asList(mapper.treeToValue(cardsData, Card[].class));

            // Se filterGithub == true, filtra apenas os que têm custom_field 11 preenchido
            if (filterGithub) {
                cards = cards.stream()
                        .filter(card -> card.customFields().stream()
                                .anyMatch(field -> field.fieldId() == 11
                                        && field.value() != null
                                        && !field.value().isEmpty()))
                        .collect(Collectors.toList());
            }

            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar cards para columnId=" + columnId + ": " + e.getMessage(), e);
        }
    }

    private boolean passedByTheColumnInPeriod(Card card, int desiredColumn,
                                              LocalDateTime from, LocalDateTime to) {
        if (card.transitions() == null || card.transitions().isEmpty()) {
            return false;
        }
        for (Transition transition : card.transitions()) {
            // Verifica se a coluna é a que queremos (31 = In Progress)
            if (transition.column_id() == desiredColumn) {
                LocalDateTime startT = parseZulu(transition.start());
                // Se 'end' for null, assume que está até "agora"
                LocalDateTime endT = (transition.end() == null)
                        ? LocalDateTime.now()
                        : parseZulu(transition.end());

                // Se o intervalo [startT, endT] cruza [from, to], então passou
                if (intervalOverlap(startT, endT, from, to)) {
                    return true;
                }
            }
        }
        return false;
    }
    private LocalDateTime parseZulu(String zuluTime) {
        return OffsetDateTime.parse(zuluTime).toLocalDateTime();
    }
    private boolean intervalOverlap(LocalDateTime start1, LocalDateTime end1,
                                    LocalDateTime start2, LocalDateTime end2) {
        // overlap se end1 >= start2 e start1 <= end2
        return !end1.isBefore(start2) && !start1.isAfter(end2);
    }

}