package com.dinoanna.eulerityassessment;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean TaskService taskService;
    @MockitoBean AiTaskService aiTaskService;

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 5, 10);

    private Task sampleTask() {
        Task task = new Task("Buy groceries", "Milk", DUE_DATE, Priority.LOW, TaskStatus.TODO);
        task.setId(1L);
        return task;
    }

    private String validRequestJson() throws Exception {
        return objectMapper.writeValueAsString(
                new TaskRequest("Buy groceries", "Milk", DUE_DATE, Priority.LOW, TaskStatus.TODO));
    }

    // POST /tasks

    @Test
    void createTask_returnsCreated() throws Exception {
        when(taskService.create(any())).thenReturn(new CreateResult.Saved(sampleTask()));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Buy groceries"));
    }

    @Test
    void createTask_acceptsNullDescription() throws Exception {
        Task taskWithoutDescription = new Task("Buy groceries", null, DUE_DATE, Priority.LOW, TaskStatus.TODO);
        taskWithoutDescription.setId(1L);
        when(taskService.create(any())).thenReturn(new CreateResult.Saved(taskWithoutDescription));

        String body = "{\"title\":\"Buy groceries\",\"dueDate\":\"2026-05-10\",\"priority\":\"LOW\",\"status\":\"TODO\"}";

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void createTask_returnsBadRequestWhenTitleBlank() throws Exception {
        String body = objectMapper.writeValueAsString(
                new TaskRequest("", "Milk", DUE_DATE, Priority.LOW, TaskStatus.TODO));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createTask_returnsBadRequestWhenDueDateMissing() throws Exception {
        String body = "{\"title\":\"Buy groceries\",\"priority\":\"LOW\",\"status\":\"TODO\"}";

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createTask_atCapReturnsConflictWithCandidates() throws Exception {
        Task existing = new Task("existing", null, DUE_DATE, Priority.HIGH, TaskStatus.TODO);
        existing.setId(5L);
        when(taskService.create(any())).thenReturn(new CreateResult.NeedsPreview(java.util.Arrays.asList(null, 5L)));
        when(taskService.findById(5L)).thenReturn(existing);
        when(taskService.demotedPriorityFor(any())).thenReturn(Priority.MEDIUM);

        String body = objectMapper.writeValueAsString(
                new TaskRequest("eleventh", null, DUE_DATE, Priority.HIGH, TaskStatus.TODO));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[0].isNew").value(true))
                .andExpect(jsonPath("$.candidates[0].title").value("eleventh"))
                .andExpect(jsonPath("$.candidates[0].taskId").doesNotExist())
                .andExpect(jsonPath("$.candidates[1].isNew").value(false))
                .andExpect(jsonPath("$.candidates[1].taskId").value(5));
    }

    @Test
    void createTask_withDemoteParam_returnsCreated() throws Exception {
        Task saved = sampleTask();
        saved.setPriority(Priority.HIGH);
        when(taskService.confirmCreate(any(), eq("new"))).thenReturn(saved);

        mockMvc.perform(post("/tasks").param("demote", "new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    // GET /tasks

    @Test
    void listTasks_returnsOkWithList() throws Exception {
        when(taskService.findAll()).thenReturn(List.of(sampleTask()));

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listTasks_returnsEmptyArray() throws Exception {
        when(taskService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // GET /tasks/{id}

    @Test
    void getTask_returnsTask() throws Exception {
        when(taskService.findById(1L)).thenReturn(sampleTask());

        mockMvc.perform(get("/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy groceries"));
    }

    @Test
    void getTask_returnsNotFoundForMissingId() throws Exception {
        when(taskService.findById(99L)).thenThrow(new NoSuchElementException("Task not found: 99"));

        mockMvc.perform(get("/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found: 99"));
    }

    // PUT /tasks/{id}

    @Test
    void updateTask_returnsUpdatedTask() throws Exception {
        Task updated = sampleTask();
        updated.setTitle("Updated title");
        when(taskService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"));
    }

    @Test
    void updateTask_returnsNotFoundForMissingId() throws Exception {
        when(taskService.update(eq(99L), any())).thenThrow(new NoSuchElementException("Task not found: 99"));

        mockMvc.perform(put("/tasks/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found: 99"));
    }

    // DELETE /tasks/{id}

    @Test
    void deleteTask_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/tasks/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTask_returnsNotFoundForMissingId() throws Exception {
        doThrow(new NoSuchElementException("Task not found: 99")).when(taskService).delete(99L);

        mockMvc.perform(delete("/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found: 99"));
    }

    // POST /tasks/suggest

    @Test
    void suggestTask_returnsAiSuggestion() throws Exception {
        AiTaskSuggestion suggestion = new AiTaskSuggestion(
                "Buy milk", null, DUE_DATE, Priority.MEDIUM, TaskStatus.TODO, "test-model", "{}");
        when(aiTaskService.suggest(any())).thenReturn(suggestion);

        mockMvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"buy milk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.model").value("test-model"));
    }

    @Test
    void suggestTask_returnsBadRequestWhenPromptBlank() throws Exception {
        mockMvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void suggestTask_returnsBadGatewayOnIllegalState() throws Exception {
        when(aiTaskService.suggest(any())).thenThrow(new IllegalStateException("Gemini returned no candidates"));

        mockMvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"buy milk\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Gemini returned no candidates"));
    }
}
