package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.AssignRequest;
import com.kanban_api.kanban_api.models.Card;
import com.kanban_api.kanban_api.services.BacklogAssignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kanban/backlog")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Backlog")
public class BacklogAssignController {

    @Autowired
    private BacklogAssignService backlogAssignService;

    /* ==============================================================
       1) Produção – coluna inteira
       ============================================================== */
    @PostMapping("/assign")
    public List<Card> assignBacklog(@RequestBody AssignRequest req) {
        int column = (req.columnId() == null) ? 29 : req.columnId();
        return backlogAssignService.assignBacklogCards(
                column,
                req.defaultOwnerUserId(),
                req.tagRules()
        );
    }

    /* ==============================================================
       2) Teste – apenas alguns cardIds
       ============================================================== */
    @PostMapping("/assignSpecific")
    public List<Card> assignSpecific(@RequestBody AssignRequest req) {
        if (req.cardIds() == null || req.cardIds().isEmpty())
            throw new IllegalArgumentException("cardIds obrigatório para assignSpecific");

        return backlogAssignService.assignSpecificCards(
                req.cardIds(),
                req.defaultOwnerUserId(),
                req.tagRules()
        );
    }
}