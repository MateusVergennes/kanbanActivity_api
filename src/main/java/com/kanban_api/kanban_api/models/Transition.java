package com.kanban_api.kanban_api.models;

public record Transition(int board_id, int workflow_id, int section, int column_id, int lane_id, String start, String end) {}
