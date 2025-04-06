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

/**
 * Service responsável pela busca de cards e geração de relatórios (Weekly e Dev).
 * Observação: Comentários em português OK, mas variáveis e métodos em inglês.
 */
@Service
public class CardService {

    // -------------------------------------------------
    // Constantes para IDs de campo/coluna importantes
    // -------------------------------------------------
    private static final int GITHUB_FIELD_ID = 11;
    private static final int IN_PROGRESS_COLUMN_ID = 31;

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

    @Autowired
    private DeployTimeService deployTimeService;

    @Autowired
    private FilterIntervalTransitionsService filterIntervalTransitionsService;
    @Autowired
    private DevIndividualReportService devIndividualReportService;

    /**
     * Gera relatório semanal (weeklyReport) com pontos incluídos.
     */
    public List<Card> generateWeeklyReport(
            String startDate,
            String endDate,
            String columnIds,
            boolean singleSheet,
            boolean filterGithub,
            boolean fillChannels,
            boolean includeDeployTime
    ) {
        try {
            // Ajuste de datas
            String resolvedStartDate = (startDate == null || startDate.isEmpty())
                    ? LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE)
                    : startDate;

            String resolvedEndDate = (endDate == null || endDate.isEmpty())
                    ? LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    : endDate;

            // Converte lista de IDs de colunas
            List<String> columnList = Arrays.asList(columnIds.split(","));

            // Busca todos os cards
            List<Card> cards = retrieveAllCards(
                    resolvedStartDate,
                    resolvedEndDate,
                    columnList,
                    filterGithub,
                    singleSheet,
                    false // includeLeadTime = false para weekly
            );

            // Salva JSON
            cardView.saveResults(cards, "weekly-results-kanban.json");

            // Busca lista de usuários (userId -> realname)
            UserResponse userResponse = userService.fetchUsers();
            List<User> allUsers = userResponse.data();

            // Se solicitado, calcula o horário de deploy
            Map<Long, LocalDateTime> deployTimes = null;
            if (includeDeployTime) {
                deployTimes = deployTimeService.findDeployTimes(cards);
            }

            // Gera Excel com pontos
            excelService.saveToExcel(
                    cards,
                    singleSheet,
                    columnList,
                    allUsers,
                    fillChannels,
                    true,           // includePoints
                    "weekly-report",
                    deployTimes
            );

            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Error generating weekly report: " + e.getMessage(), e);
        }
    }

    /**
     * Gera relatório de desenvolvimento (DevReport).
     */
    public List<Card> generateDevReport(
            String startDate,
            String endDate,
            String columnIds,
            boolean singleSheet,
            boolean filterGithub,
            boolean fillChannels,
            boolean weeklyStipulatedCalculation,
            boolean filterBystipulatedHours,
            boolean resultsByDev,
            double legendaryThreshold
    ) {
        try {
            // Ajuste de datas
            String resolvedStartDate = (startDate == null || startDate.isEmpty())
                    ? LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE)
                    : startDate;

            String resolvedEndDate = (endDate == null || endDate.isEmpty())
                    ? LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    : endDate;

            // Converte lista de IDs de colunas
            List<String> columnList = Arrays.asList(columnIds.split(","));

            // Busca todos os cards (leadTime incluído)
            List<Card> cards = retrieveAllCards(
                    resolvedStartDate,
                    resolvedEndDate,
                    columnList,
                    filterGithub,
                    singleSheet,
                    true // includeLeadTime = true para dev
            );

            // Filtra localmente os que passaram pela coluna "IN PROGRESS" dentro do período
            LocalDateTime from = LocalDate.parse(resolvedStartDate).atStartOfDay();
            LocalDateTime to = LocalDate.parse(resolvedEndDate).atTime(23, 59, 59);

            cards = cards.stream()
                    .filter(card -> wasCardInColumnDuringPeriod(card, IN_PROGRESS_COLUMN_ID, from, to))
                    .collect(Collectors.toList());

            //SE weeklyStipulatedCalculation && filterBystipulatedHours => exclui os que não têm Horas Estipuladas
            if (weeklyStipulatedCalculation && filterBystipulatedHours) {
                cards = cards.stream()
                        .filter(card -> {
                            if (card.customFields() == null) return false;
                            return card.customFields().stream()
                                    .anyMatch(cf ->
                                            cf.fieldId() == 9 &&
                                                    cf.value() != null &&
                                                    !cf.value().isEmpty()
                                    );
                        })
                        .collect(Collectors.toList());
            }

            // Salva JSON
            cardView.saveResults(cards, "dev-results-kanban.json");

            // Busca lista de usuários
            UserResponse userResponse = userService.fetchUsers();
            List<User> allUsers = userResponse.data();

            // Carrega colunas do board=4, workflow=6 (ajuste se preciso)
            List<Column> columns = columnService.getColumns(4, 6L);

            // Gera Excel com colunas dinâmicas (relatório principal)
            excelService.saveToExcelDevDynamic(
                    cards,
                    singleSheet,
                    columnList,
                    allUsers,
                    fillChannels,
                    "dev-report",
                    columns,
                    from,
                    to,
                    weeklyStipulatedCalculation,
                    resultsByDev,
                    legendaryThreshold
            );

            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Error generating dev report: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // MÉTODOS PRIVADOS
    // ============================================================

    /**
     * Busca todos os cards para múltiplas colunas, unindo em uma lista só.
     */
    private List<Card> retrieveAllCards(
            String startDate,
            String endDate,
            List<String> columnList,
            boolean filterGithub,
            boolean singleSheet,
            boolean includeLeadTime
    ) {
        List<Card> result = new ArrayList<>();
        if (singleSheet) {
            // Apenas 1 requisição combinada
            String combinedCols = String.join(",", columnList);
            result = retrieveCardsByColumn(startDate, endDate, combinedCols, filterGithub, includeLeadTime);
        } else {
            // 1 requisição por coluna
            for (String col : columnList) {
                List<Card> subset = retrieveCardsByColumn(startDate, endDate, col, filterGithub, includeLeadTime);
                result.addAll(subset);
            }
        }

        String fallbackStart = (startDate == null || startDate.isEmpty())
                ? LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE)
                : startDate;
        String fallbackEnd = (endDate == null || endDate.isEmpty())
                ? LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                : endDate;
        LocalDate startLD = LocalDate.parse(fallbackStart);
        LocalDate endLD = LocalDate.parse(fallbackEnd);

        // Filtra cartões que não tiveram transições no período
        result = filterIntervalTransitionsService.filterCardsWithoutTransitionsInPeriod(result, startLD, endLD);

        return result;
    }

    /**
     * Faz a chamada REST para buscar cards de uma ou mais colunas (separadas por vírgula).
     */
    private List<Card> retrieveCardsByColumn(
            String startDate,
            String endDate,
            String columnId,
            boolean filterGithub,
            boolean includeLeadTime
    ) {
        try {
            String expandParam = includeLeadTime
                    ? "custom_fields,tag_ids,lead_time_per_column,transitions"
                    : "custom_fields,tag_ids,transitions";

            String url = String.format(
                    "%s/cards?last_modified_from_date=%s&last_modified_to_date=%s"
                            + "&per_page=1000&column_ids=%s&expand=%s",
                    kanbanConfig.getApiUrl(),
                    startDate,
                    endDate,
                    columnId,
                    expandParam
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

            // Se filterGithub == true, filtra os que possuem campo GITHUB_FIELD_ID preenchido
            if (filterGithub) {
                cards = cards.stream()
                        .filter(card -> card.customFields().stream()
                                .anyMatch(field ->
                                        field.fieldId() == GITHUB_FIELD_ID
                                                && field.value() != null
                                                && !field.value().isEmpty()
                                ))
                        .collect(Collectors.toList());
            }

            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving cards for columnId=" + columnId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Verifica se o card passou pela coluna desejada no intervalo [from, to].
     */
    private boolean wasCardInColumnDuringPeriod(Card card, int desiredColumnId,
                                                LocalDateTime from, LocalDateTime to) {
        if (card.transitions() == null || card.transitions().isEmpty()) {
            return false;
        }
        for (Transition transition : card.transitions()) {
            if (transition.column_id() == desiredColumnId) {
                LocalDateTime startT = parseZulu(transition.start());
                // end nulo => até "agora"
                LocalDateTime endT = (transition.end() == null)
                        ? LocalDateTime.now()
                        : parseZulu(transition.end());

                // Se [startT, endT] cruza [from, to], então passou
                if (intervalsOverlap(startT, endT, from, to)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Converte string Zulu/UTC para LocalDateTime.
     */
    private LocalDateTime parseZulu(String zuluTime) {
        return OffsetDateTime.parse(zuluTime).toLocalDateTime();
    }

    /**
     * Verifica sobreposição entre [start1, end1] e [start2, end2].
     */
    private boolean intervalsOverlap(LocalDateTime start1, LocalDateTime end1,
                                     LocalDateTime start2, LocalDateTime end2) {
        // overlap se end1 >= start2 e start1 <= end2
        return !end1.isBefore(start2) && !start1.isAfter(end2);
    }
}