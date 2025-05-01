package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.services.BacklogAssignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/kanban/backlog")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Backlog")
public class BacklogAssignController {

    @Autowired
    private BacklogAssignService backlogAssignService;

    /* ======================================================================
       1) Produção – coluna inteira
       ====================================================================== */
    @Operation(
            summary = "Atribui todos os cards da coluna (default 29) para um usuário",
            description = """
                  1. Busca todos os cards da coluna informada (Backlog).  
                  2. Gera snapshot JSON.  
                  3. Executa /cards/updateMany atribuindo owner_user_id.
            """
    )
    @ApiResponse(responseCode = "200", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = Card.class)))
    @PostMapping("/assign")
    public List<Card> assignBacklogToUser(
            @Parameter(description = "column_id da coluna; default 29")
            @RequestParam(name = "column_id", defaultValue = "29") int columnId,
            @Parameter(description = "owner_user_id que receberá os cards")
            @RequestParam(name = "owner_user_id") long ownerUserId
    ) {
        return backlogAssignService.assignBacklogCards(columnId, ownerUserId);
    }

    /* ======================================================================
       2) Teste – apenas cardIds específicos
       ====================================================================== */
    @Operation(
            summary = "TESTE: atribui somente os card_ids fornecidos",
            description = """
                  Envie no corpo JSON:

                  {
                    "card_ids": [123, 456],
                    "owner_user_id": 16
                  }
            """
    )
    @ApiResponse(responseCode = "200", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = Card.class)))
    @PostMapping("/assignSpecific")
    public List<Card> assignSpecific(
            @RequestBody Map<String, Object> body
    ) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("card_ids");
        if (rawIds == null || rawIds.isEmpty())
            throw new IllegalArgumentException("card_ids é obrigatório");

        long owner = ((Number) body.get("owner_user_id")).longValue();
        List<Long> ids = rawIds.stream().map(Long::valueOf).toList();

        return backlogAssignService.assignSpecificCards(ids, owner);
    }
}