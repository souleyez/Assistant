package com.souleyez.assistant.controller;

import com.souleyez.assistant.service.AppStateStore;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/jobs")
public class JobController {
  private final AppStateStore store;

  public JobController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping
  public Map<String, Object> jobs() {
    return Collections.<String, Object>singletonMap("items", store.snapshot().getJobs());
  }

  @GetMapping("/{id}/logs")
  public Map<String, Object> logs(@PathVariable String id) throws IOException {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("jobId", id);
    response.put("lines", store.readJobLog(id, 120));
    return response;
  }

  @PostMapping
  public Map<String, Object> start(@RequestBody StartJobRequest request) throws IOException {
    return Collections.<String, Object>singletonMap("item", store.startJob(request.getProjectId()));
  }

  @PostMapping("/{id}/complete")
  public Map<String, Object> complete(@PathVariable String id) throws IOException {
    return Collections.<String, Object>singletonMap("item", store.completeJob(id));
  }

  @PostMapping("/{id}/cancel")
  public Map<String, Object> cancel(@PathVariable String id) throws IOException {
    store.cancelJob(id);
    return Collections.<String, Object>singletonMap("message", "cancelled");
  }

  public static class StartJobRequest {
    @NotBlank
    private String projectId;

    public String getProjectId() {
      return projectId;
    }

    public void setProjectId(String projectId) {
      this.projectId = projectId;
    }
  }
}
