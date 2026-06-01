package com.dinoanna.eulerityassessment;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    TaskRepository taskRepository;

    @Mock
    PlatformTransactionManager txManager;

    @InjectMocks
    TaskService taskService;

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 5, 10);

    private TaskRequest sampleRequest() {
        return new TaskRequest("Buy groceries", "Milk and eggs", DUE_DATE, Priority.LOW, TaskStatus.TODO);
    }

    private Task sampleTask(Long id) {
        Task task = new Task("Buy groceries", "Milk and eggs", DUE_DATE, Priority.LOW, TaskStatus.TODO);
        task.setId(id);
        return task;
    }

    @Test
    void create_savesAndReturnsTask() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask(1L));

        CreateResult result = taskService.create(sampleRequest());

        assertThat(result).isInstanceOf(CreateResult.Saved.class);
        Task task = ((CreateResult.Saved) result).task();
        assertThat(task.getId()).isEqualTo(1L);
        assertThat(task.getTitle()).isEqualTo("Buy groceries");
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void findAll_returnsList() {
        when(taskRepository.findAll()).thenReturn(List.of(sampleTask(1L), sampleTask(2L)));

        assertThat(taskService.findAll()).hasSize(2);
    }

    @Test
    void findAll_returnsEmptyList() {
        when(taskRepository.findAll()).thenReturn(List.of());

        assertThat(taskService.findAll()).isEmpty();
    }

    @Test
    void findById_returnsTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask(1L)));

        assertThat(taskService.findById(1L).getId()).isEqualTo(1L);
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_updatesFieldsAndSaves() {
        Task existing = sampleTask(1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(existing)).thenReturn(existing);

        Task result = taskService.update(1L,
                new TaskRequest("Updated title", null, DUE_DATE, Priority.HIGH, TaskStatus.DONE));

        assertThat(result.getTitle()).isEqualTo("Updated title");
        assertThat(result.getDescription()).isNull();
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(99L, sampleRequest()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void delete_deletesTask() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        taskService.delete(1L);

        verify(taskRepository).deleteById(1L);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(taskRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }
}
