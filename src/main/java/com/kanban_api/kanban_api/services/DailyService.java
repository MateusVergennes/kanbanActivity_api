package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.ColumnResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class DailyService {

    @Autowired
    private KanbanConfig kanbanConfig;

    private List<Card> fetchCards(String startDate, String endDate, String columnIds, String userIds) {
        try {
            // Construção dinâmica da URL sem parâmetros vazios
            StringJoiner urlBuilder = new StringJoiner("&", kanbanConfig.getApiUrl() + "/cards?", "");

            urlBuilder.add("last_modified_from_date=" + startDate);
            urlBuilder.add("last_modified_to_date=" + endDate);
            urlBuilder.add("expand=custom_fields");

            if (columnIds != null && !columnIds.isEmpty()) {
                urlBuilder.add("column_ids=" + columnIds);
            }
            if (userIds != null && !userIds.isEmpty()) {
                urlBuilder.add("owner_user_ids=" + userIds);
            }

            String url = urlBuilder.toString();

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

            return List.of(mapper.treeToValue(cardsData, Card[].class));

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar cards: " + e.getMessage());
        }
    }

    @Transactional
    public List<Card> getDaily(String startDate, String endDate, List<Long> columnIds, List<Long> userIds) {
        // Converte List<Long> para String somente se houver valores
        String columnIdsStr = (columnIds != null && !columnIds.isEmpty())
                ? columnIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                : null;

        String userIdsStr = (userIds != null && !userIds.isEmpty())
                ? userIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                : null;

        return fetchCards(startDate, endDate, columnIdsStr, userIdsStr);
    }

    @Transactional
    public ColumnResponse fetchColumns() {
        try {
            String url = kanbanConfig.getApiUrl() + "/boards/" + kanbanConfig.getBoardId() + "/columns";
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", kanbanConfig.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response.getBody(), ColumnResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar colunas: " + e.getMessage());
        }
    }

}
