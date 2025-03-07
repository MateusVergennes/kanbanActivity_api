package com.kanban_api.kanban_api.models;

public record Tag(int tag_id, String label, String color, int availability, int is_enabled) {
}
