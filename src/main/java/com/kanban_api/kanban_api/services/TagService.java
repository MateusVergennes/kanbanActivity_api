package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.CardTag;
import com.kanban_api.kanban_api.models.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class TagService {

    @Autowired
    private KanbanConfig kanbanConfig;

    /**
     * Obtém todas as tags cadastradas no Kanbanize.
     */
    public List<Tag> getAllTags() {
        try {
            String url = kanbanConfig.getApiUrl() + "/tags";

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", kanbanConfig.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode dataNode = root.path("data");

            Tag[] tagsArray = mapper.treeToValue(dataNode, Tag[].class);
            return Arrays.asList(tagsArray);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar todas as tags: " + e.getMessage());
        }
    }

    /**
     * Obtém a lista de IDs das tags ligadas a um card.
     * Se doDelay == true, faz Thread.sleep(2000) antes da requisição
     * para evitar Too Many Requests (429).
     */
    public List<CardTag> getCardTags(int cardId, boolean doDelay) {
        try {
            if (doDelay) {
                Thread.sleep(2000);
            }

            String url = kanbanConfig.getApiUrl() + "/cards/" + cardId + "/tags";

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", kanbanConfig.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode dataNode = root.path("data");

            CardTag[] cardTagsArray = mapper.treeToValue(dataNode, CardTag[].class);
            return Arrays.asList(cardTagsArray);

        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new RuntimeException("Kanbanize retornou 429 Too Many Requests (cardId=" + cardId + ")", ex);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrompida ao aguardar antes de chamar /cards/" + cardId + "/tags", e);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar tags do cardId " + cardId + ": " + e.getMessage(), e);
        }
    }
}