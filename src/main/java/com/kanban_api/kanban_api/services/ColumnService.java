package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.Column;
import com.kanban_api.kanban_api.models.ColumnResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ColumnService {

    @Autowired
    private KanbanConfig kanbanConfig;

    /**
     * Obtém as colunas de um board específico via Kanbanize API,
     * depois retorna todas ou filtra por workflow_id, se informado.
     *
     * @param boardId     ID do board (ex: 4)
     * @param workflowId  Se != null, filtra apenas colunas cujo workflow_id == workflowId
     */
    public List<Column> getColumns(int boardId, Long workflowId) {
        try {
            String url = String.format(
                    "%s/boards/%d/columns",
                    kanbanConfig.getApiUrl(),
                    boardId
            );

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

            // Converte para array do tipo Column
            Column[] columnsArray = mapper.treeToValue(dataNode, Column[].class);
            List<Column> columns = Arrays.asList(columnsArray);

            // Se workflowId != null, filtra
            if (workflowId != null) {
                columns = columns.stream()
                        .filter(col -> col.workflow_id() == workflowId)
                        .collect(Collectors.toList());
            }

            return columns;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erro ao buscar colunas do board " + boardId + ": " + e.getMessage(), e
            );
        }
    }
}
