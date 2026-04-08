package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {
  private final AppStateStore store;

  public DatasourceController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping("/definitions")
  public Map<String, Object> definitions() {
    return Collections.<String, Object>singletonMap("items", store.snapshot().getDatasources());
  }

  @GetMapping("/runs")
  public Map<String, Object> runs() {
    return Collections.<String, Object>singletonMap("items", store.snapshot().getDatasourceRuns());
  }

  @PostMapping("/definitions")
  public Map<String, Object> create(@RequestBody AppState.DatasourceDefinition request) throws IOException {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", store.upsertDatasource(request));
    return response;
  }

  @PatchMapping("/definitions/{id}")
  public Map<String, Object> update(@PathVariable String id, @RequestBody AppState.DatasourceDefinition request) throws IOException {
    request.setId(id);
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", store.upsertDatasource(request));
    return response;
  }

  @PostMapping("/definitions/{id}/{action}")
  public Map<String, Object> action(@PathVariable String id, @PathVariable String action) throws IOException {
    store.changeDatasourceStatus(id, action);
    return Collections.<String, Object>singletonMap("message", "ok");
  }

  @DeleteMapping("/definitions/{id}")
  public Map<String, Object> delete(@PathVariable String id) throws IOException {
    store.deleteDatasource(id);
    return Collections.<String, Object>singletonMap("message", "deleted");
  }

  @DeleteMapping("/runs/{id}")
  public Map<String, Object> deleteRun(@PathVariable String id) throws IOException {
    store.deleteRun(id);
    return Collections.<String, Object>singletonMap("message", "deleted");
  }
}
