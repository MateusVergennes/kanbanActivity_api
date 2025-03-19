package com.kanban_api.kanban_api.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban_api.kanban_api.models.Card;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
public class CardView {

    private static final String OUTPUT_DIR = "output";

    public void saveResults(List<Card> cards, String fileName) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Monta o path completo usando fileName
            String filePath = OUTPUT_DIR + "/" + fileName;

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File(filePath), cards);

            System.out.println("✅ JSON salvo em: " + filePath);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar JSON: " + e.getMessage());
        }
    }
}
