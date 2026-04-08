package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BotController {
  private final AppStateStore store;

  public BotController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping("/bots")
  public Map<String, Object> bots() {
    AppState state = store.snapshot();
    return Collections.<String, Object>singletonMap("items", state.getBots());
  }
}
