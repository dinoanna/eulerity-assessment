package com.dinoanna.eulerityassessment;

import java.time.LocalDate;
import java.util.ArrayList;
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

    // ─── HIGH-priority cap ───

    private Task highTask(long id, LocalDate dueDate) {
        Task task = new Task("task" + id, null, dueDate, Priority.HIGH, TaskStatus.TODO);
        task.setId(id);
        return task;
    }

    private void stubTxAndCap(long highCount) {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(taskRepository.countByPriority(Priority.HIGH)).thenReturn(highCount);
    }

    private TaskRequest highRequest(LocalDate dueDate) {
        return new TaskRequest("new", null, dueDate, Priority.HIGH, TaskStatus.TODO);
    }

    @Test
    void create_belowHighCap_returnsSaved() {
        stubTxAndCap(5L);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        CreateResult result = taskService.create(highRequest(LocalDate.now().plusDays(7)));

        assertThat(result).isInstanceOf(CreateResult.Saved.class);
        assertThat(((CreateResult.Saved) result).task().getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void create_atHighCap_returnsNeedsPreviewWithTwoCandidates() {
        stubTxAndCap(10L);
        LocalDate today = LocalDate.now();
        List<Task> existing = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            existing.add(highTask(i, today.plusDays(i)));
        }
        when(taskRepository.findByPriority(Priority.HIGH)).thenReturn(existing);

        CreateResult result = taskService.create(highRequest(today.plusDays(1)));

        assertThat(result).isInstanceOf(CreateResult.NeedsPreview.class);
        assertThat(((CreateResult.NeedsPreview) result).candidateIds()).hasSize(2);
    }

    @Test
    void pickCandidates_pastDueRanksAboveFuture() {
        stubTxAndCap(10L);
        LocalDate today = LocalDate.now();
        List<Task> existing = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            LocalDate due = (i == 5) ? today.minusDays(10) : today.plusDays(i);
            existing.add(highTask(i, due));
        }
        when(taskRepository.findByPriority(Priority.HIGH)).thenReturn(existing);

        CreateResult result = taskService.create(highRequest(today.plusDays(20)));

        List<Long> ids = ((CreateResult.NeedsPreview) result).candidateIds();
        assertThat(ids.get(0)).isEqualTo(5L);
    }

    @Test
    void pickCandidates_withinPastDue_mostOverdueFirst() {
        stubTxAndCap(10L);
        LocalDate today = LocalDate.now();
        List<Task> existing = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            existing.add(highTask(i, today.minusDays(i * 10)));
        }
        when(taskRepository.findByPriority(Priority.HIGH)).thenReturn(existing);

        CreateResult result = taskService.create(highRequest(today.plusDays(1)));

        List<Long> ids = ((CreateResult.NeedsPreview) result).candidateIds();
        assertThat(ids).containsExactly(10L, 9L);
    }

    @Test
    void pickCandidates_withinFuture_farthestFutureFirst() {
        stubTxAndCap(10L);
        LocalDate today = LocalDate.now();
        List<Task> existing = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            existing.add(highTask(i, today.plusDays(i * 10)));
        }
        when(taskRepository.findByPriority(Priority.HIGH)).thenReturn(existing);

        CreateResult result = taskService.create(highRequest(today.plusDays(5)));

        List<Long> ids = ((CreateResult.NeedsPreview) result).candidateIds();
        assertThat(ids).containsExactly(10L, 9L);
    }

    @Test
    void pickCandidates_tiebreakByIdAscending() {
        stubTxAndCap(10L);
        LocalDate sameDate = LocalDate.now().plusDays(30);
        List<Task> existing = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            existing.add(highTask(i, sameDate));
        }
        when(taskRepository.findByPriority(Priority.HIGH)).thenReturn(existing);

        CreateResult result = taskService.create(highRequest(sameDate));

        List<Long> ids = ((CreateResult.NeedsPreview) result).candidateIds();
        assertThat(ids).containsExactly(1L, 2L);
    }

    @Test
    void pickCandidates_newTaskInBottomTwo_whenWeakestClaim() {
        stubTxAndCap(10L);
        LocalDate today = LocalDate.now();
        List<Task> existing = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            existing.add(highTask(i, today.plusDays(i)));
        }
        when(taskRepository.findByPriority(Priority.HIGH)).thenReturn(existing);

        CreateResult result = taskService.create(highRequest(today.plusDays(100)));

        List<Long> ids = ((CreateResult.NeedsPreview) result).candidateIds();
        assertThat(ids.get(0)).isNull();
        assertThat(ids.get(1)).isEqualTo(10L);
    }

    @Test
    void pickCandidates_newTaskNotCandidate_whenStrongestClaim() {
        stubTxAndCap(10L);
        LocalDate today = LocalDate.now();
        List<Task> existing = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            existing.add(highTask(i, today.minusDays(i)));
        }
        when(taskRepository.findByPriority(Priority.HIGH)).thenReturn(existing);

        CreateResult result = taskService.create(highRequest(today.plusDays(1)));

        List<Long> ids = ((CreateResult.NeedsPreview) result).candidateIds();
        assertThat(ids).doesNotContainNull();
    }

    @Test
    void confirmCreate_demoteNew_savesNewAtDemotedPriority() {
        stubTxAndCap(10L);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = taskService.confirmCreate(highRequest(LocalDate.now().plusDays(10)), "new");

        assertThat(result.getPriority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void confirmCreate_demoteExisting_demotesItAndSavesNewAsHigh() {
        stubTxAndCap(10L);
        LocalDate today = LocalDate.now();
        Task existing = highTask(5L, today.minusDays(30));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = taskService.confirmCreate(highRequest(today.plusDays(1)), "5");

        assertThat(existing.getPriority()).isEqualTo(Priority.LOW);
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void confirmCreate_capFreedBetweenPreviewAndCommit_savesAsHigh() {
        stubTxAndCap(9L);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = taskService.confirmCreate(highRequest(LocalDate.now().plusDays(10)), "new");

        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void confirmCreate_staleDemoteTarget_throws() {
        stubTxAndCap(10L);
        Task notHigh = new Task("task", null, LocalDate.now(), Priority.MEDIUM, TaskStatus.TODO);
        notHigh.setId(5L);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(notHigh));

        assertThatThrownBy(() -> taskService.confirmCreate(highRequest(LocalDate.now().plusDays(1)), "5"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void demotedPriorityFor_pastDate_returnsLow() {
        assertThat(taskService.demotedPriorityFor(LocalDate.now().minusDays(1))).isEqualTo(Priority.LOW);
    }

    @Test
    void demotedPriorityFor_today_returnsMedium() {
        assertThat(taskService.demotedPriorityFor(LocalDate.now())).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void demotedPriorityFor_futureDate_returnsMedium() {
        assertThat(taskService.demotedPriorityFor(LocalDate.now().plusDays(1))).isEqualTo(Priority.MEDIUM);
    }
}
