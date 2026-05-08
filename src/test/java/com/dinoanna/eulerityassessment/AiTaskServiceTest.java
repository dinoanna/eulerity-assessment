package com.dinoanna.eulerityassessment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class AiTaskServiceTest {

    @Mock RestClient.Builder restClientBuilder;
    @Mock RestClient restClient;
    @Mock(answer = Answers.RETURNS_SELF) RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        doReturn(restClientBuilder).when(restClientBuilder).baseUrl(any(String.class));
        doReturn(restClientBuilder).when(restClientBuilder).requestFactory(any());
        doReturn(restClient).when(restClientBuilder).build();
    }

    private AiTaskService serviceWithKey(String key) {
        return new AiTaskService(restClientBuilder, objectMapper, "https://test.com", key, "test-model");
    }

    private void stubRestClientChain() {
        doReturn(requestBodyUriSpec).when(restClient).post();
        doReturn(responseSpec).when(requestBodyUriSpec).retrieve();
    }

    private Map<String, Object> geminiResponse(String taskJson) {
        return Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", taskJson))))));
    }

    @Test
    void suggest_parsesValidResponse() {
        stubRestClientChain();
        String json = "{\"title\":\"Buy milk\",\"description\":\"From the store\","
                + "\"dueDate\":\"2026-05-10\",\"priority\":\"LOW\",\"status\":\"TODO\"}";
        doReturn(geminiResponse(json)).when(responseSpec).body(Map.class);

        AiTaskSuggestion result = serviceWithKey("valid-key").suggest(new TaskSuggestionRequest("buy milk"));

        assertThat(result.getTitle()).isEqualTo("Buy milk");
        assertThat(result.getDescription()).isEqualTo("From the store");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(result.getPriority()).isEqualTo(Priority.LOW);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(result.getModel()).isEqualTo("test-model");
    }

    @Test
    void suggest_throwsUnauthorizedWhenApiKeyBlank() {
        assertThatThrownBy(() -> serviceWithKey("").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void suggest_throwsUnauthorizedOn401FromGemini() {
        stubRestClientChain();
        doThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED)).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("bad-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void suggest_throwsUnauthorizedOn403FromGemini() {
        stubRestClientChain();
        doThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN)).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("bad-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void suggest_throwsBadGatewayOnOtherHttpError() {
        stubRestClientChain();
        doThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("valid-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void suggest_throwsGatewayTimeoutOnTimeout() {
        stubRestClientChain();
        doThrow(new ResourceAccessException("Connection timed out")).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("valid-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void suggest_throwsIllegalStateOnNullResponse() {
        stubRestClientChain();
        doReturn(null).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("valid-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void suggest_throwsIllegalStateOnEmptyCandidates() {
        stubRestClientChain();
        doReturn(Map.of("candidates", List.of())).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("valid-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no candidates");
    }

    @Test
    void suggest_throwsIllegalStateOnMalformedJson() {
        stubRestClientChain();
        doReturn(geminiResponse("not-valid-json")).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("valid-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-JSON");
    }

    @Test
    void suggest_throwsIllegalStateOnMissingRequiredField() {
        stubRestClientChain();
        String json = "{\"description\":\"desc\",\"dueDate\":\"2026-05-10\",\"priority\":\"MEDIUM\",\"status\":\"TODO\"}";
        doReturn(geminiResponse(json)).when(responseSpec).body(Map.class);

        assertThatThrownBy(() -> serviceWithKey("valid-key").suggest(new TaskSuggestionRequest("do something")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("title");
    }
}
