package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.UserResponse;
import com.kanban_api.kanban_api.services.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kanban/users")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Users")
public class UsersController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public ResponseEntity<UserResponse> getUsers() {
        return ResponseEntity.status(HttpStatus.OK).body(userService.fetchUsers());
    }

}
