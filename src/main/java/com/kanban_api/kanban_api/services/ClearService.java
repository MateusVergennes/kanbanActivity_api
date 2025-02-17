package com.kanban_api.kanban_api.services;

import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ClearService {

    private static final String OUTPUT_DIR = "output";

    /**
     * Remove arquivos JSON/Excel da pasta "output", mantendo o .gitkeep.
     *
     * @return Mensagem de sucesso ou erro.
     */
    public String clearGeneratedFiles() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);

            // Verifica se a pasta existe
            if (!Files.exists(outputPath)) {
                return "A pasta '" + OUTPUT_DIR + "' não existe.";
            }

            File directory = outputPath.toFile();
            File[] files = directory.listFiles();

            if (files == null || files.length == 0) {
                return "A pasta '" + OUTPUT_DIR + "' já está vazia.";
            }

            for (File file : files) {
                if (!file.getName().equals(".gitkeep")) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        return "Erro ao remover o arquivo: " + file.getName();
                    }
                }
            }

            return "✅ Arquivos da pasta '" + OUTPUT_DIR + "' foram removidos com sucesso.";

        } catch (Exception e) {
            return "Erro ao limpar arquivos: " + e.getMessage();
        }
    }

}
