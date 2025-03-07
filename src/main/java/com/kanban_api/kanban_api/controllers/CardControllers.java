package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.services.CardService;
import com.kanban_api.kanban_api.services.ClearService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/kanban/cards")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Cards")
public class CardControllers {

    @Autowired
    private CardService cardService;

    @Autowired
    private ClearService clearService;

    /**
     * Retorna os cards do Kanban dentro de um período e gera um relatório em Excel.
     *
     * Parâmetro extra:
     * - fill_channels: se true, preenche a coluna "Canal" (mais lento, pois chama /cards/{cardId}/tags e aguarda 2s entre cada chamada).
     */
    @Operation(
            summary = "Lista os cards do Kanban dentro de um período",
            description = "Retorna os cards obtidos da API externa dentro do intervalo de datas fornecido. "
                    + "Se nenhum intervalo for informado, os últimos 7 dias serão considerados por padrão."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Lista de cards filtrados com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Card.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = "Erro interno ao processar a requisição",
            content = @Content(mediaType = "application/json")
    )
    @GetMapping
    public List<Card> getKanbanCards(
            @Parameter(description = "Data de início (YYYY-MM-DD). Padrão: Últimos 7 dias.")
            @RequestParam(required = false, name = "start_date") String startDate,

            @Parameter(description = "Data de fim (YYYY-MM-DD). Padrão: Hoje.")
            @RequestParam(required = false, name = "end_date") String endDate,

            @Parameter(description = "IDs das colunas separadas por vírgula. Exemplo: 32,164,163.")
            @RequestParam(defaultValue = "32,164,163", name = "column_ids") String columnIds,

            @Parameter(description = "Se true, gera uma única planilha combinada. Se false, gera uma planilha para cada column_id.")
            @RequestParam(defaultValue = "true", name = "single_sheet") boolean singleSheet,

            @Parameter(description = "Se true, retorna apenas os cards com link do GitHub. Se false, retorna todos os cards.")
            @RequestParam(defaultValue = "true", name = "filter_github") boolean filterGithub,

            @Parameter(description = "Se true, preenche a coluna 'Canal' (mais lento). Se false, a coluna fica vazia. Isso acontece pois, ele aguarda 2s antes de cada request de tags para evitar 429 Too Many Requests")
            @RequestParam(defaultValue = "true", name = "fill_channels") boolean fillChannels
    ) {
        return cardService.getCards(
                startDate,
                endDate,
                columnIds,
                singleSheet,
                filterGithub,
                fillChannels
        );
    }

    /**
     * Remove arquivos da pasta "output", mantendo o .gitkeep.
     */
    @Operation(
            summary = "Limpa arquivos gerados na pasta output",
            description = "Remove os arquivos JSON e Excel baixados, mantendo o .gitkeep."
    )
    @ApiResponse(responseCode = "200", description = "Arquivos removidos com sucesso")
    @ApiResponse(responseCode = "500", description = "Erro ao limpar arquivos")
    @PostMapping
    public Map<String, String> clearGeneratedFiles() {
        String message = clearService.clearGeneratedFiles();
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return response;
    }

}