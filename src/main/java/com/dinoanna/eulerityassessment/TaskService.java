package com.dinoanna.eulerityassessment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskService {

    static final int MAX_HIGH_PRIORITY_TASKS = 10;

    private final TaskRepository taskRepository;
    private final TransactionTemplate transactionTemplate;
    private final ReentrantLock highPriorityLock = new ReentrantLock();

    public TaskService(TaskRepository taskRepository, PlatformTransactionManager txManager) {
        this.taskRepository = taskRepository;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    public CreateResult create(TaskRequest request) {
        if (request.priority() != Priority.HIGH) {
            Task saved = transactionTemplate.execute(status -> save(request, request.priority()));
            return new CreateResult.Saved(saved);
        }

        highPriorityLock.lock();
        try {
            return transactionTemplate.execute(status -> {
                if (taskRepository.countByPriority(Priority.HIGH) < MAX_HIGH_PRIORITY_TASKS) {
                    return new CreateResult.Saved(save(request, Priority.HIGH));
                }
                return new CreateResult.NeedsPreview(pickDemotionCandidateIds(request));
            });
        } finally {
            highPriorityLock.unlock();
        }
    }

    public Task confirmCreate(TaskRequest request, String demote) {
        if (request.priority() != Priority.HIGH) {
            return transactionTemplate.execute(status -> save(request, request.priority()));
        }

        highPriorityLock.lock();
        try {
            return transactionTemplate.execute(status -> {
                if (taskRepository.countByPriority(Priority.HIGH) < MAX_HIGH_PRIORITY_TASKS) {
                    return save(request, Priority.HIGH);
                }
                if ("new".equals(demote)) {
                    return save(request, demotedPriorityFor(request.dueDate()));
                }
                long demoteId;
                try {
                    demoteId = Long.parseLong(demote);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid demote target: " + demote);
                }
                Task existing = taskRepository.findById(demoteId)
                        .orElseThrow(() -> new NoSuchElementException("Task not found: " + demoteId));
                if (existing.getPriority() != Priority.HIGH) {
                    throw new IllegalStateException("Selected task is no longer HIGH; please retry.");
                }
                existing.setPriority(demotedPriorityFor(existing.getDueDate()));
                taskRepository.save(existing);
                return save(request, Priority.HIGH);
            });
        } finally {
            highPriorityLock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + id));
    }

    @Transactional
    public Task update(Long id, TaskRequest request) {
        Task task = findById(id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority());
        task.setStatus(request.status());
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new NoSuchElementException("Task not found: " + id);
        }
        taskRepository.deleteById(id);
    }

    public Priority demotedPriorityFor(LocalDate dueDate) {
        return dueDate.isBefore(LocalDate.now()) ? Priority.LOW : Priority.MEDIUM;
    }

    private Task save(TaskRequest request, Priority priority) {
        Task task = new Task(
                request.title(),
                request.description(),
                request.dueDate(),
                priority,
                request.status());
        return taskRepository.save(task);
    }

    private List<@Nullable Long> pickDemotionCandidateIds(TaskRequest pending) {
        List<Task> existing = taskRepository.findByPriority(Priority.HIGH);
        LocalDate today = LocalDate.now();

        record Entry(@Nullable Long id, LocalDate dueDate, long sortKey) {}

        List<Entry> entries = new ArrayList<>(existing.size() + 1);
        for (Task t : existing) {
            Long taskId = t.getId();
            long key = taskId == null ? Long.MAX_VALUE : taskId;
            entries.add(new Entry(taskId, t.getDueDate(), key));
        }
        entries.add(new Entry(null, pending.dueDate(), Long.MAX_VALUE));

        Comparator<Entry> tierThenDate = (a, b) -> {
            boolean aPast = a.dueDate().isBefore(today);
            boolean bPast = b.dueDate().isBefore(today);
            if (aPast != bPast) {
                return aPast ? -1 : 1;
            }
            if (aPast) {
                return a.dueDate().compareTo(b.dueDate());
            }
            return b.dueDate().compareTo(a.dueDate());
        };

        entries.sort(tierThenDate.thenComparingLong(Entry::sortKey));

        List<@Nullable Long> result = new ArrayList<>(2);
        result.add(entries.get(0).id());
        result.add(entries.get(1).id());
        return result;
    }
}
