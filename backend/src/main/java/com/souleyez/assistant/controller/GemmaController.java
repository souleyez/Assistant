package com.souleyez.assistant.controller;

import com.souleyez.assistant.service.AppStateStore;
import com.souleyez.assistant.service.GemmaRuntimeService;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/gemma-assistant")
public class GemmaController {
  private final AppStateStore store;
  private final GemmaRuntimeService gemmaRuntimeService;

  public GemmaController(AppStateStore store, GemmaRuntimeService gemmaRuntimeService) {
    this.store = store;
    this.gemmaRuntimeService = gemmaRuntimeService;
  }

  @GetMapping
  public Map<String, Object> conversations() {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    Map<String, Object> runtime = new LinkedHashMap<String, Object>();
    runtime.put("ready", gemmaRuntimeService.isAvailable());
    runtime.put("model", gemmaRuntimeService.getConfiguredModel());
    response.put("items", store.snapshot().getGemmaConversations());
    response.put("suggestedFocuses", new String[] {"dataset", "training", "deployment"});
    response.put("runtime", runtime);
    return response;
  }

  @PostMapping
  public Map<String, Object> ask(@RequestBody AskGemmaRequest request) throws IOException {
    return Collections.<String, Object>singletonMap(
        "item",
        store.askGemma(gemmaRuntimeService.ask(request.getPrompt(), request.getFocus()), request.getPrompt(), request.getFocus())
    );
  }

  public static class AskGemmaRequest {
    @NotBlank
    private String prompt;
    private String focus;

    public String getPrompt() {
      return prompt;
    }

    public void setPrompt(String prompt) {
      this.prompt = prompt;
    }

    public String getFocus() {
      return focus;
    }

    public void setFocus(String focus) {
      this.focus = focus;
    }
  }
}
