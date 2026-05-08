package com.dinoanna.eulerityassessment;

import jakarta.validation.constraints.NotBlank;

public class TaskSuggestionRequest {

	@NotBlank
	private String prompt;

	@SuppressWarnings("NullAway")
	public TaskSuggestionRequest() {
	}

	public TaskSuggestionRequest(String prompt) {
		this.prompt = prompt;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
}
