package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
  private final AppStateStore store;

  public DatasetController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping
  public Map<String, Object> datasets() {
    return Collections.<String, Object>singletonMap("items", store.snapshot().getDatasets());
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody AppState.Dataset request) throws IOException {
    return Collections.<String, Object>singletonMap("item", store.createDataset(request));
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> delete(@PathVariable String id) throws IOException {
    store.deleteDataset(id);
    return Collections.<String, Object>singletonMap("message", "deleted");
  }
}
