package com.dinoanna.eulerityassessment;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AiTaskService {

    private static final Logger log = LoggerFactory.getLogger(AiTaskService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;

    public AiTaskService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.model:gemini-2.5-flash}") String model) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(15000);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
        this.apiKey = apiKey;
    }

    public AiTaskSuggestion suggest(TaskSuggestionRequest request) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing Gemini API key. Set GEMINI_API_KEY.");
        }

        String prompt = request.getPrompt().trim();
        String systemPrompt = "You turn plain-language task descriptions into JSON objects with fields "
                + "title, description, dueDate, priority, and status. "
                + "Use ISO-8601 dates, choose priority from LOW, MEDIUM, HIGH, and choose status from TODO, IN_PROGRESS, DONE. "
                + "If prompt omits due date, use tomorrow. If prompt omits priority, use MEDIUM. "
                + "If prompt omits status, use TODO. "
                + "For description, only include it if the prompt contains genuinely extra detail beyond the task title — otherwise set it to null. Do not repeat or rephrase the prompt as the description. "
                + "Return only valid JSON and no markdown.";

        Map<String, Object> payload = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"));

        Map<?, ?> response;
        try {
            response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1beta/models/{model}:generateContent")
                    .queryParam("key", apiKey)
                    .build(model))
                .body(payload)
                .retrieve()
                .body(Map.class);
        } catch (HttpClientErrorException exception) {
            log.error("Gemini HTTP error {} — body: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Gemini API key is invalid or not authorized", exception);
            }

            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Gemini request failed: " + exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            log.error("Gemini request timed out: {}", exception.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Gemini request timed out after 15 seconds", exception);
        }

        if (response == null) {
            throw new IllegalStateException("Gemini response body was empty");
        }

        List<?> candidates = (List<?>) Objects.requireNonNull(response.get("candidates"), "Missing Gemini candidates");
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates");
        }

        Map<?, ?> firstCandidate = asMap(candidates.get(0), "candidate");
        Object contentObject = Objects.requireNonNull(firstCandidate.get("content"), "Missing candidate.content");
        Map<?, ?> contentNode = asMap(contentObject, "candidate.content");
        Object partsObject = Objects.requireNonNull(contentNode.get("parts"), "Missing candidate.content.parts");
        List<?> parts = asList(partsObject, "candidate.content.parts");
        if (parts.isEmpty()) {
            throw new IllegalStateException("Gemini candidate had no parts");
        }

        Map<?, ?> firstPart = asMap(parts.get(0), "candidate.content.parts[0]");
        String content = String.valueOf(Objects.requireNonNull(firstPart.get("text"), "Missing part text"));

        Map<?, ?> parsed = parseJson(content);

        String title = getRequiredString(parsed, "title");
        String description = getNullableString(parsed, "description");
        LocalDate dueDate = LocalDate.parse(getRequiredString(parsed, "dueDate"));
        Priority priority = Priority.valueOf(getRequiredString(parsed, "priority").trim().toUpperCase(Locale.ROOT));
        TaskStatus status = TaskStatus.valueOf(getRequiredString(parsed, "status").trim().toUpperCase(Locale.ROOT));

        return new AiTaskSuggestion(
                title,
                description,
                dueDate,
                priority,
                status,
                model,
                content);
    }

    private Map<?, ?> parseJson(String content) {
        try {
            return objectMapper.readValue(content, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Gemini returned non-JSON content: " + content, exception);
        }
    }

    private static Map<?, ?> asMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected object at " + fieldName);
        }
        return map;
    }

    private static List<?> asList(Object value, String fieldName) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("Expected list at " + fieldName);
        }
        return list;
    }

    private static String getRequiredString(Map<?, ?> source, String field) {
        String value = getNullableString(source, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("AI response did not include required field: " + field);
        }
        return value;
    }

    private static @Nullable String getNullableString(Map<?, ?> source, String field) {
        Object value = source.get(field);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
