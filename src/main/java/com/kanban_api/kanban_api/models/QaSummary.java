package com.kanban_api.kanban_api.models;

import java.util.Map;

public record QaSummary(int totalCardsWithSubtasks, int totalCardsOverall, int totalSubtasks, Map<String, Long> cardsByDeveloper, Map<String, Long> cardsByTeam) { }