package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.models.TagRule;
import com.kanban_api.kanban_api.views.CardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
       Produção – coluna completa
       ======================================================================= */
    public List<Card> assignBacklogCards(
            int columnId,
            Long defaultOwner,
            List<TagRule> tagRules
    ) {
        try {
            int targetColumn = (columnId <= 0) ? DEFAULT_BACKLOG_COLUMN : columnId;
            String url = String.format(
                    "%s/cards?column_ids=%d&per_page=1000&expand=tag_ids",
                    kanbanConfig.getApiUrl(), targetColumn);

            List<Card> cards = fetchCards(url);
            saveSnapshot(cards, "backlog-snapshot");

            List<CardUpdate> updates = buildUpdates(cards, defaultOwner, tagRules);
            if (!updates.isEmpty()) postUpdateMany(updates);

            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Erro em assignBacklogCards: " + e.getMessage(), e);
        }
    }

    /* =======================================================================
       Teste – somente os cardIds fornecidos
       ======================================================================= */
    public List<Card> assignSpecificCards(
            List<Long> cardIds,
            Long defaultOwner,
            List<TagRule> tagRules
    ) {
        if (cardIds == null || cardIds.isEmpty())
            throw new IllegalArgumentException("cardIds não pode estar vazio");

        try {
            String joined = cardIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            String url = String.format(
                    "%s/cards?card_ids=%s&expand=tag_ids",
                    kanbanConfig.getApiUrl(), joined);

            List<Card> cards = fetchCards(url);
            saveSnapshot(cards, "specific-snapshot");

            List<CardUpdate> updates = buildUpdates(cards, defaultOwner, tagRules);
            if (!updates.isEmpty()) postUpdateMany(updates);

            return cards;
        } catch (Exception e) {
            throw new RuntimeException("Erro em assignSpecificCards: " + e.getMessage(), e);
        }
    }

    /* -----------------------------------------------------------------------
       Helpers
       ----------------------------------------------------------------------- */

    /** Faz GET e devolve lista de Card. */
    private List<Card> fetchCards(String url) throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class);

        JsonNode cardsNode = mapper.readTree(resp.getBody())
                .path("data").path("data");

        return Arrays.asList(mapper.treeToValue(cardsNode, Card[].class));
    }

    private void saveSnapshot(List<Card> cards, String prefix) throws Exception {
        String ts = java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        cardView.saveResults(cards, prefix + "-" + ts + ".json");
    }

    /** Constrói lista (card_id, owner_user_id) obedecendo tagRules + default. */
    private List<CardUpdate> buildUpdates(
            List<Card> cards,
            Long defaultOwner,
            List<TagRule> tagRules
    ) {
        // tagId -> ownerUserId
        Map<Long, Long> tagMap = (tagRules == null)
                ? Collections.emptyMap()
                : tagRules.stream()
                .collect(Collectors.toMap(TagRule::tagId, TagRule::ownerUserId));

        List<CardUpdate> updates = new ArrayList<>();

        for (Card c : cards) {
            Long chosenOwner = null;

            if (c.tagIds() != null && !c.tagIds().isEmpty()) {
                for (Integer tagIdInt : c.tagIds()) {
                    long tagId = tagIdInt.longValue();      // *** conversão ***
                    Long ruleOwner = tagMap.get(tagId);
                    if (ruleOwner != null && ruleOwner > 0) { // 0 = “ignorar”
                        chosenOwner = ruleOwner;
                        break;   // primeira coincidência vence
                    }
                }
            }
            if (chosenOwner == null) chosenOwner = defaultOwner;
            if (chosenOwner != null && chosenOwner > 0)
                updates.add(new CardUpdate(c.cardId(), chosenOwner));
        }
        return updates;
    }

    /** Envia POST /cards/updateMany com owners específicos por card. */
    private void postUpdateMany(List<CardUpdate> updates) throws Exception {
        ArrayNode arr = mapper.createArrayNode();
        for (CardUpdate upd : updates) {
            ObjectNode n = mapper.createObjectNode();
            n.put("card_id", upd.cardId());
            n.put("owner_user_id", upd.ownerUserId());
            arr.add(n);
        }
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

    /* record interno apenas para compor updateMany */
    private record CardUpdate(long cardId, long ownerUserId) {}
}