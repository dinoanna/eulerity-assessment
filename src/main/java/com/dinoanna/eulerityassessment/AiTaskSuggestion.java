package com.dinoanna.eulerityassessment;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

public class AiTaskSuggestion {

        private String title;
        private @Nullable String description;
        private LocalDate dueDate;
        private Priority priority;
        private TaskStatus status;
        private String model;
        private String rawResponse;

        @SuppressWarnings("NullAway")
        public AiTaskSuggestion() {
        }

        public AiTaskSuggestion(
                        String title,
                        @Nullable String description,
                        LocalDate dueDate,
                        Priority priority,
                        TaskStatus status,
                        String model,
                        String rawResponse) {
                this.title = title;
                this.description = description;
                this.dueDate = dueDate;
                this.priority = priority;
                this.status = status;
                this.model = model;
                this.rawResponse = rawResponse;
        }

        public String getTitle() {
                return title;
        }

        public void setTitle(String title) {
                this.title = title;
        }

        public @Nullable String getDescription() {
                return description;
        }

        public void setDescription(@Nullable String description) {
                this.description = description;
        }

        public LocalDate getDueDate() {
                return dueDate;
        }

        public void setDueDate(LocalDate dueDate) {
                this.dueDate = dueDate;
        }

        public Priority getPriority() {
                return priority;
        }

        public void setPriority(Priority priority) {
                this.priority = priority;
        }

        public TaskStatus getStatus() {
                return status;
        }

        public void setStatus(TaskStatus status) {
                this.status = status;
        }

        public String getModel() {
                return model;
        }

        public void setModel(String model) {
                this.model = model;
        }

        public String getRawResponse() {
                return rawResponse;
        }

        public void setRawResponse(String rawResponse) {
                this.rawResponse = rawResponse;
        }
}
