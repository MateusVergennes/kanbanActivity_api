package com.kanban_api.kanban_api.models;

public record Subtask(
        int     subtaskId,
        String  description,
        Integer ownerUserId,
        String  finishedAt,
        String  deadline,
        int     position) { }
