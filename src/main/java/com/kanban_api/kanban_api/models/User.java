package com.kanban_api.kanban_api.models;

import java.util.List;

public record User(Long user_id, String username, String realname, String avatar, List<String> attributes) {
}
