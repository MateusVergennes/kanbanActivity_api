package com.kanban_api.kanban_api.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kanban_api.kanban_api.config.KanbanConfig;
import com.kanban_api.kanban_api.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gera o relatório de Quality Assurance (cards + subtasks).
 */
@Service
public class QualityAssuranceService {

    private static final String OUTPUT_DIR       = "output/";
    private static final int    PAGE_SIZE        = 200;
    private static final String EXPAND           = "tag_ids,subtasks,custom_fields";
    private static final int    GITHUB_FIELD_ID  = 11;

    @Autowired private KanbanConfig kanbanConfig;
    @Autowired private TagService   tagService;
    @Autowired private UserService  userService;

    /* ========================================================= */
    public QaSummary generateReport(boolean filterGithub, String createdFromDate) {

        // 1) Busca todos os cards do board
        List<Card> allCards = fetchAllCards(createdFromDate);

        // 2) (Opcional) mantém só os que têm pull-request
        if (filterGithub) {
            allCards = allCards.stream()
                    .filter(this::hasPullRequest)
                    .toList();
        }

        // 3) Separa os que possuem subtasks
        List<Card> cardsWithSubtasks = allCards.stream()
                .filter(c -> c.subtasks() != null && !c.subtasks().isEmpty())
                .toList();

        // 4) Total de subtasks
        int totalSubtasks = cardsWithSubtasks.stream()
                .mapToInt(c -> c.subtasks().size())
                .sum();

        // 5) Detalhes + Resumo
        List<QaCardDetails> details =
                mapToDetails(cardsWithSubtasks);
        QaSummary summary =
                buildSummary(details, allCards.size(), totalSubtasks);

        // 6) Grava arquivos
        saveFiles(details, summary);
        return summary;
    }

    /* ------------------------------ */
    private List<Card> fetchAllCards(String createdFromDate) {

        List<Card> result = new ArrayList<>();
        RestTemplate rest = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

        int currentPage = 1, allPages;

        do {
            String url;
            if (createdFromDate != null && !createdFromDate.isBlank()) {
                url = String.format("%s/cards?created_from_date=%s&per_page=%d&page=%d&expand=%s",
                        kanbanConfig.getApiUrl(), URLEncoder.encode(createdFromDate, StandardCharsets.UTF_8), PAGE_SIZE, currentPage, EXPAND);
            } else {
                url = String.format("%s/cards?per_page=%d&page=%d&expand=%s",
                        kanbanConfig.getApiUrl(), PAGE_SIZE, currentPage, EXPAND);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", kanbanConfig.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> rsp = rest.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            try {
                var root = mapper.readTree(rsp.getBody()).path("data");

                allPages    = root.path("pagination").path("all_pages").asInt();
                currentPage = root.path("pagination").path("current_page").asInt() + 1;

                Card[] pageCards =
                        mapper.treeToValue(root.path("data"), Card[].class);

                Collections.addAll(result, pageCards);

            } catch (JsonProcessingException e) {
                throw new RuntimeException(
                        "Erro parseando JSON da página " + currentPage, e);
            }

        } while (currentPage <= allPages);

        return result;
    }

    /* ------------------------------ */
    private List<QaCardDetails> mapToDetails(List<Card> cards) {

        Map<Long, User> userMap = userService.fetchUsers()
                .data()
                .stream()
                .collect(Collectors.toMap(
                        User::user_id, u -> u));

        Map<Integer, String> tagMap = tagService.getAllTags()
                .stream()
                .collect(Collectors.toMap(
                        Tag::tag_id, Tag::label));

        return cards.stream().map(c -> {
            String dev  = Optional.ofNullable(userMap.get((long) c.ownerUserId()))
                    .map(User::realname).orElse("");
            String team = Optional.ofNullable(c.tagIds())
                    .orElse(List.of())
                    .stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));

            return new QaCardDetails(
                    c.cardId(),
                    c.customId(),
                    c.title(),
                    dev,
                    team,
                    c.subtasks().size(),
                    c.subtasks(),
                    hasPullRequest(c)          // NOVO campo
            );
        }).toList();
    }

    /* ------------------------------ */
    private QaSummary buildSummary(List<QaCardDetails> details,
                                   int totalCardsOverall,
                                   int totalSubtasks) {

        Map<String, Long> byDev  = details.stream()
                .collect(Collectors.groupingBy(
                        QaCardDetails::developer,
                        Collectors.summingLong(QaCardDetails::subtaskCount)));

        Map<String, Long> byTeam = details.stream()
                .collect(Collectors.groupingBy(
                        QaCardDetails::team,
                        Collectors.summingLong(QaCardDetails::subtaskCount)));

        return new QaSummary(
                details.size(),      // totalCardsWithSubtasks
                totalCardsOverall,   // totalCardsOverall (já filtrado pelo bool)
                totalSubtasks,       // totalSubtasks
                byDev,
                byTeam);
    }

    /* ------------------------------ */
    private void saveFiles(List<QaCardDetails> details, QaSummary summary) {

        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) dir.mkdir();

        String ts = LocalDateTime.now()
                .format(DateTimeFormatter
                        .ofPattern("yyyy-MM-dd_HH-mm"));

        writeJson(OUTPUT_DIR + "resultsOfQualityAssurance_AllCards_" + ts + ".json",
                details);
        writeJson(OUTPUT_DIR + "resultsOfQualityAssurance_" + ts + ".json",
                summary);
    }

    private void writeJson(String path, Object obj) {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            ObjectMapper om = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
            fos.write(om.writeValueAsString(obj)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Erro salvando arquivo " + path, e);
        }
    }

    /* ------------------------------ */
    private boolean hasPullRequest(Card c) {
        if (c.customFields() == null) return false;
        return c.customFields().stream()
                .anyMatch(cf ->
                        cf.fieldId() == GITHUB_FIELD_ID &&
                                cf.value()   != null           &&
                                !cf.value().isBlank());
    }


    /* =========================================================
     *                          boardSnapshot
     * ======================================================= */
    public BoardSnapshot generateBoardSnapshot(boolean filterGithub,
                                               String createdFromDate) {

        // Reaproveita fetchAllCards (já valida data)
        List<Card> cards = fetchAllCards(createdFromDate);

        if (filterGithub) {
            cards = cards.stream().filter(this::hasPullRequest).toList();
        }

        // ---------- mapas auxiliares ----------
        Map<Integer, String> columnNameMap = Map.ofEntries(
                Map.entry(29 , "BACKLOG"),
                Map.entry(30 , "TO DO"),
                Map.entry(31 , "IN PROGRESS"),
                Map.entry(32 , "DONE"),
                Map.entry(33 , "READY TO ARCHIVE"),
                Map.entry(73 , "CODE REVIEW"),
                Map.entry(74 , "QA TEST"),
                Map.entry(76 , "READY TO DEPLOY"),
                Map.entry(81 , "READY FOR QA"),
                Map.entry(163, "CLIENT DEMO"),
                Map.entry(164, "DEPLOYED")
        );

        Map<String, Integer>  totalByColumn        = new HashMap<>();
        Map<String, Map<String,Integer>> byColTag  = new HashMap<>();
        Map<String, Integer>  totalByDev           = new HashMap<>();
        Map<String, Map<String,Integer>> byColDev  = new HashMap<>();

        Map<Long, User> userMap = userService.fetchUsers()
                .data()
                .stream()
                .collect(Collectors.toMap(
                        User::user_id, u -> u));

        Map<Integer, String> tagMap = tagService.getAllTags()
                .stream()
                .collect(Collectors.toMap(
                        Tag::tag_id, Tag::label));

        for (Card c : cards) {
            String column = columnNameMap.getOrDefault(c.columnId(),
                    "COL_"+c.columnId());
            totalByColumn.merge(column, 1, Integer::sum);

            // -------- coluna × tag --------
            List<Integer> tags = Optional.ofNullable(c.tagIds())
                    .orElse(List.of());
            String tagLabel = tags.stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            if (tagLabel.isBlank()) tagLabel = "(no-tag)";

            byColTag.computeIfAbsent(column, k -> new HashMap<>())
                    .merge(tagLabel, 1, Integer::sum);

            // -------- dev -----------
            String dev = Optional.ofNullable(userMap.get((long)c.ownerUserId()))
                    .map(User::realname).orElse("(unassigned)");
            totalByDev.merge(dev, 1, Integer::sum);

            byColDev.computeIfAbsent(column, k -> new HashMap<>())
                    .merge(dev, 1, Integer::sum);
        }

        BoardSnapshot snap = new BoardSnapshot(
                totalByColumn,
                byColTag,
                totalByDev,
                byColDev);

        saveSnapshotFile(snap);   // grava JSON
        return snap;
    }

    /* ========= grava arquivo ========= */
    private void saveSnapshotFile(BoardSnapshot snap) {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) dir.mkdir();

        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));

        writeJson(OUTPUT_DIR + "boardSnapshot_" + ts + ".json", snap);
    }


}