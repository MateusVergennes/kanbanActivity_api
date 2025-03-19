package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.Column;
import com.kanban_api.kanban_api.services.ColumnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kanban/columns")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Columns")
public class ColumnController {

    @Autowired
    private ColumnService columnService;

    @Operation(
            summary = "Retorna as colunas de um board",
            description = """
            Retorna as colunas do board informado (por padrão board=4) 
            e filtra por workflow_id (por padrão, 6 = 'desenvolvimento web dia-a-dia').
            Se quiser retornar todas as colunas sem filtrar, use workflow_id = 0 ou algo do tipo.
            """
    )
    @GetMapping
    public List<Column> getColumns(
            @Parameter(description = "ID do board (padrão = 4)")
            @RequestParam(defaultValue = "4", name = "board_id") int boardId,

            @Parameter(description = "ID do workflow (padrão = 6, para desenvolvimento web dia-a-dia)")
            @RequestParam(defaultValue = "6", name = "workflow_id") long workflowId
    ) {
        // Se quiser permitir 'workflowId=0' para não filtrar, podemos fazer:
        Long wf = (workflowId == 0) ? null : workflowId;

        return columnService.getColumns(boardId, wf);
    }
}
