package com.dinoanna.eulerityassessment;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    private static Long taskId;
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 6, 1);

    @Test
    @Order(1)
    void createTask_returnsCreatedWithId() {
        TaskRequest request = new TaskRequest("Integration Task", "End-to-end test", DUE_DATE, Priority.MEDIUM, TaskStatus.TODO);

        ResponseEntity<Task> response = restTemplate.postForEntity("/tasks", request, Task.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Integration Task");
        assertThat(response.getBody().getStatus()).isEqualTo(TaskStatus.TODO);

        taskId = response.getBody().getId();
    }

    @Test
    @Order(2)
    void listTasks_containsCreatedTask() {
        ResponseEntity<Task[]> response = restTemplate.getForEntity("/tasks", Task[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(Arrays.stream(response.getBody()).anyMatch(t -> taskId.equals(t.getId()))).isTrue();
    }

    @Test
    @Order(3)
    void getTask_returnsCorrectTask() {
        ResponseEntity<Task> response = restTemplate.getForEntity("/tasks/" + taskId, Task.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Integration Task");
        assertThat(response.getBody().getPriority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    @Order(4)
    void updateTask_reflectsChanges() {
        TaskRequest update = new TaskRequest("Updated Task", null, DUE_DATE, Priority.HIGH, TaskStatus.IN_PROGRESS);

        ResponseEntity<Task> response = restTemplate.exchange(
                "/tasks/" + taskId, HttpMethod.PUT, new HttpEntity<>(update), Task.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Updated Task");
        assertThat(response.getBody().getDescription()).isNull();
        assertThat(response.getBody().getPriority()).isEqualTo(Priority.HIGH);
        assertThat(response.getBody().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @Order(5)
    void deleteTask_returnsNoContent() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/tasks/" + taskId, HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(6)
    void getDeletedTask_returns404() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/tasks/" + taskId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("error");
    }
}
