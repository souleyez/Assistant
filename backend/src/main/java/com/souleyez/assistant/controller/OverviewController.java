package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import com.souleyez.assistant.service.GemmaRuntimeService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OverviewController {
  private final AppStateStore store;
  private final GemmaRuntimeService gemmaRuntimeService;

  public OverviewController(AppStateStore store, GemmaRuntimeService gemmaRuntimeService) {
    this.store = store;
    this.gemmaRuntimeService = gemmaRuntimeService;
  }

  @GetMapping("/overview")
  public Map<String, Object> overview() {
    AppState state = store.snapshot();
    Map<String, Object> metrics = new LinkedHashMap<String, Object>();
    metrics.put("datasetCount", state.getDatasets().size());
    metrics.put("projectCount", state.getProjects().size());
    metrics.put("runningJobs", countJobs(state, "running"));
    metrics.put("modelCount", state.getModels().size());

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("metrics", metrics);
    state.getPlatform().getRuntime().setGemmaModel(gemmaRuntimeService.getConfiguredModel());
    state.getPlatform().getRuntime().setGemmaReady(gemmaRuntimeService.isAvailable());
    response.put("platform", state.getPlatform());
    response.put("latestJobs", state.getJobs());
    response.put("latestModels", state.getModels());
    response.put("timeline", store.recentTimeline(8));
    response.put("gemmaConversations", state.getGemmaConversations());
    response.put("quickStarts", state.getQuickStarts());
    return response;
  }

  private int countJobs(AppState state, String status) {
    int count = 0;
    for (AppState.TrainingJob job : state.getJobs()) {
      if (status.equals(job.getStatus())) {
        count++;
      }
    }
    return count;
  }
}
