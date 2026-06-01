package com.dinoanna.eulerityassessment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final AiTaskService aiTaskService;

    public TaskController(TaskService taskService, AiTaskService aiTaskService) {
        this.taskService = taskService;
        this.aiTaskService = aiTaskService;
    }

    @PostMapping
    public ResponseEntity<?> createTask(
            @Valid @RequestBody TaskRequest request,
            @RequestParam(required = false) @Nullable String demote) {
        if (demote != null) {
            Task saved = taskService.confirmCreate(request, demote);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }
        CreateResult result = taskService.create(request);
        if (result instanceof CreateResult.Saved saved) {
            return ResponseEntity.status(HttpStatus.CREATED).body(saved.task());
        }
        CreateResult.NeedsPreview preview = (CreateResult.NeedsPreview) result;
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildPreviewResponse(preview.candidateIds(), request));
    }

    @GetMapping
    public List<Task> listTasks() {
        return taskService.findAll();
    }

    @GetMapping("/{id}")
    public Task getTask(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id) {
        taskService.delete(id);
    }

    @PostMapping("/suggest")
    public AiTaskSuggestion suggestTask(@Valid @RequestBody TaskSuggestionRequest request) {
        return aiTaskService.suggest(request);
    }

    private Map<String, Object> buildPreviewResponse(List<@Nullable Long> ids, TaskRequest pending) {
        List<Map<String, Object>> candidates = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Map<String, Object> entry = new HashMap<>();
            if (id == null) {
                entry.put("isNew", true);
                entry.put("taskId", null);
                entry.put("title", pending.title());
                entry.put("dueDate", pending.dueDate().toString());
                entry.put("currentPriority", pending.priority().name());
                entry.put("newPriority", taskService.demotedPriorityFor(pending.dueDate()).name());
            } else {
                Task t = taskService.findById(id);
                entry.put("isNew", false);
                entry.put("taskId", t.getId());
                entry.put("title", t.getTitle());
                entry.put("dueDate", t.getDueDate().toString());
                entry.put("currentPriority", t.getPriority().name());
                entry.put("newPriority", taskService.demotedPriorityFor(t.getDueDate()).name());
            }
            candidates.add(entry);
        }
        return Map.of("candidates", candidates);
    }
}
