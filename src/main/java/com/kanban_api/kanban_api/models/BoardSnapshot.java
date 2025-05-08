package com.kanban_api.kanban_api.models;

import java.util.Map;

public record BoardSnapshot(Map<String, Integer> totalByColumn, Map<String, Map<String, Integer>> byColumnByTag, Map<String, Integer> totalByDeveloper, Map<String, Map<String, Integer>> byColumnByDeveloper) { }