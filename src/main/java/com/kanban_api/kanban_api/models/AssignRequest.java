package com.kanban_api.kanban_api.models;

import java.util.List;

public record AssignRequest(Integer columnId, List<Long> cardIds, Long defaultOwnerUserId, List<TagRule> tagRules) {}