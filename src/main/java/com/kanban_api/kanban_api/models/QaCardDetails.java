package com.kanban_api.kanban_api.models;

import java.util.List;

public record QaCardDetails(int cardId, String customId, String title, String developer, String team, int subtaskCount, List<Subtask> subtasks, boolean hasPullRequest) { }