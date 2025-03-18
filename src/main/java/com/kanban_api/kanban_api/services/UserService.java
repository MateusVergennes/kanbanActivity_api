package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.UserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class UserService {

    @Autowired
    private KanbanConfig kanbanConfig;

    @Transactional
    public UserResponse fetchUsers() {
        try {
            String url = kanbanConfig.getApiUrl() + "/users";
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", kanbanConfig.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response.getBody(), UserResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar usuários: " + e.getMessage());
        }
    }

}
