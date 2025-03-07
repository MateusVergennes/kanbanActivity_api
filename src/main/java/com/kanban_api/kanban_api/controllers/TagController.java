package com.kanban_api.kanban_api.controllers;

import com.kanban_api.kanban_api.models.CardTag;
import com.kanban_api.kanban_api.models.Tag;
import com.kanban_api.kanban_api.services.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kanban/tags")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TagController {

    @Autowired
    private TagService tagService;

    @GetMapping
    public List<Tag> getAllTags() {
        return tagService.getAllTags();
    }

    /**
     * Se quiser passar delayForToManyRequests, pode alterar a assinatura:
     * ex.: @GetMapping("/{cardId}") public List<CardTag> getCardTags(@PathVariable int cardId,
     *  @RequestParam(defaultValue="false") boolean delayForToManyRequests) { ... }
     */
    @GetMapping("/{cardId}")
    public List<CardTag> getCardTags(@PathVariable int cardId) {
        // Por padrão, false
        return tagService.getCardTags(cardId, false);
    }
}
