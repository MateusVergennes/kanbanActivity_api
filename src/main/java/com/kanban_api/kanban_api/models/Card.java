package com.kanban_api.kanban_api.models;

import java.util.List;

public record Card(int cardId, String customId, int boardId, int workflowId, String title, int ownerUserId, Integer typeId, String color,
                   int section, int columnId, int laneId, int position, List<CustomField> customFields) {
}
