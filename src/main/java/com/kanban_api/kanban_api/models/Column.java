package com.kanban_api.kanban_api.models;

public record Column(Long column_id, Long workflow_id, Integer section, Long parent_column_id, Integer position,String name,
        String description, String color, Integer limit, Integer cards_per_row, Integer flow_type, String card_ordering
) {}