package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.views.CardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service que:
 *  1) Faz snapshot JSON do estado atual dos cards.
 *  2) Atribui owner_user_id usando /cards/updateMany.
 */
@Service
public class BacklogAssignService {

    private static final int DEFAULT_BACKLOG_COLUMN = 29;

    @Autowired
    private KanbanConfig kanbanConfig;

    @Autowired
    private CardView cardView;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /* =======================================================================
       PRODUÇÃO – processa todos os cards de uma coluna (padrão 29).
       ======================================================================= */
    public List<Card> assignBacklogCards(int columnId, long ownerUserId) {
        try {
            int targetColumn = (columnId <= 0) ? DEFAULT_BACKLOG_COLUMN : columnId;

            String url = String.format("%s/cards?column_ids=%d&per_page=1000",
                    kanbanConfig.getApiUrl(), targetColumn);

            JsonNode cardsNode = performGet(url);
            List<Card> cards = Arrays.asList(mapper.treeToValue(cardsNode, Card[].class));

            saveSnapshot(cards, "backlog-snapshot");

            if (!cards.isEmpty()) {
                /* ------------- CONVERSÃO EXPLÍCITA PARA Long ------------- */
                List<Long> ids = cards.stream()
                        .map(Card::cardId)
                        .map(Long::valueOf)
                        .collect(Collectors.toList());

                postUpdateMany(ids, ownerUserId);
            }
            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Erro em assignBacklogCards: " + e.getMessage(), e);
        }
    }

    /* =======================================================================
       TESTE – processa somente os cardIds fornecidos.
       ======================================================================= */
    public List<Card> assignSpecificCards(List<Long> cardIds, long ownerUserId) {
        if (cardIds == null || cardIds.isEmpty())
            throw new IllegalArgumentException("cardIds não pode estar vazio");

        try {
            // 1. GET apenas os IDs desejados
            String joined = cardIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            String url = String.format("%s/cards?card_ids=%s", kanbanConfig.getApiUrl(), joined);

            JsonNode cardsNode = performGet(url);
            List<Card> cards = Arrays.asList(mapper.treeToValue(cardsNode, Card[].class));

            // 2. Snapshot
            saveSnapshot(cards, "specific-snapshot");

            // 3. updateMany
            postUpdateMany(cardIds, ownerUserId);

            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Erro em assignSpecificCards: " + e.getMessage(), e);
        }
    }

    /* -----------------------------------------------------------------------
       Helpers
       ----------------------------------------------------------------------- */

    /** Executa GET e devolve o nó data.data */
    private JsonNode performGet(String url) throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class);
        return mapper.readTree(resp.getBody()).path("data").path("data");
    }

    /** Salva JSON com timestamp para histórico. */
    private void saveSnapshot(List<Card> cards, String prefix) throws Exception {
        String ts = java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        cardView.saveResults(cards, prefix + "-" + ts + ".json");
    }

    /** POST /cards/updateMany com a lista de IDs. */
    private void postUpdateMany(List<Long> cardIds, long ownerUserId) throws Exception {
        ArrayNode arr = mapper.createArrayNode();
        cardIds.forEach(id -> {
            ObjectNode n = mapper.createObjectNode();
            n.put("card_id", id);
            n.put("owner_user_id", ownerUserId);
            arr.add(n);
        });
        ObjectNode payload = mapper.createObjectNode().set("cards", arr);

        String url = kanbanConfig.getApiUrl() + "/cards/updateMany";
        restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(mapper.writeValueAsString(payload), buildHeaders()),
                String.class
        );
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("apikey", kanbanConfig.getApiKey());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }
}