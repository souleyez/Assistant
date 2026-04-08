package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HomeController {
  private final AppStateStore store;

  public HomeController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping("/home")
  public Map<String, Object> home() {
    AppState state = store.snapshot();
    Map<String, Object> metrics = new LinkedHashMap<String, Object>();
    metrics.put("libraryCount", state.getLibraries().size());
    metrics.put("documentCount", state.getDocuments().size());
    metrics.put("datasourceCount", state.getDatasources().size());
    metrics.put("templateCount", state.getTemplates().size());

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("metrics", metrics);
    response.put("model", state.getModel());
    response.put("bots", state.getBots());
    response.put("timeline", store.recentTimeline(6));
    return response;
  }
}
