package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
  private final AppStateStore store;

  public ProjectController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping
  public Map<String, Object> projects() {
    return Collections.<String, Object>singletonMap("items", store.snapshot().getProjects());
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody AppState.TrainingProject request) throws IOException {
    return Collections.<String, Object>singletonMap("item", store.createProject(request));
  }
}
