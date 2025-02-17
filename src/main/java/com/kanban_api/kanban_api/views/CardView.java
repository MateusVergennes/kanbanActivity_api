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
    private static final String FILE_PATH = OUTPUT_DIR + "/results-kanban.json";

    public void saveResults(List<Card> cards) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) {
                directory.mkdir();
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File(FILE_PATH), cards);

            System.out.println("✅ JSON salvo em: " + FILE_PATH);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar JSON: " + e.getMessage());
        }
    }

}
