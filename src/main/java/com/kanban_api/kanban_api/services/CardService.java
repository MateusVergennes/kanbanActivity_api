package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.User;
import com.kanban_api.kanban_api.models.UserResponse;
import com.kanban_api.kanban_api.views.CardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
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
    private DailyService dailyService;

    private List<Card> fetchCards(String startDate, String endDate, String columnId, boolean filterGithub) {
        try {
            String url = String.format(
                    "%s/cards?last_modified_from_date=%s&last_modified_to_date=%s&column_ids=%s&expand=custom_fields",
                    kanbanConfig.getApiUrl(), startDate, endDate, columnId
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

            // Filtra cards que têm custom_field 11 (Github) se filterGithub for true
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
            throw new RuntimeException("Erro ao buscar cards para columnId " + columnId + ": " + e.getMessage());
        }
    }

    /**
     * Se fillChannels == true, a planilha terá a coluna "Canal" preenchida (fazendo requisições /cards/{cardId}/tags).
     */
    public List<Card> getCards(
            String startDate,
            String endDate,
            String columnIds,
            boolean singleSheet,
            boolean filterGithub,
            boolean fillChannels
    ) {
        try {
            if (startDate == null || startDate.isEmpty()) {
                startDate = LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE);
            }
            if (endDate == null || endDate.isEmpty()) {
                endDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            }

            List<String> columnList = Arrays.asList(columnIds.split(","));
            List<Card> allCards = new ArrayList<>();

            if (singleSheet) {
                // Uma única requisição para todos os column_ids
                allCards = fetchCards(startDate, endDate, columnIds, filterGithub);
            } else {
                // Múltiplas requisições, uma para cada columnId
                for (String columnId : columnList) {
                    List<Card> cards = fetchCards(startDate, endDate, columnId, filterGithub);
                    allCards.addAll(cards);
                }
            }

            // Salvar em JSON (fluxo original)
            cardView.saveResults(allCards);

            // Obter usuários
            UserResponse userResponse = dailyService.fetchUsers();
            List<User> allUsers = userResponse.data();

            // Gerar relatório em Excel
            excelService.saveToExcel(
                    allCards,
                    singleSheet,
                    columnList,
                    allUsers,
                    fillChannels
            );

            return allCards;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao obter os cards: " + e.getMessage());
        }
    }
}