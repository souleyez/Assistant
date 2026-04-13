package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/models")
public class ModelController {
  private final AppStateStore store;

  public ModelController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping
  public Map<String, Object> models() {
    return Collections.<String, Object>singletonMap("items", store.snapshot().getModels());
  }

  @PostMapping
  public Map<String, Object> register(@RequestBody AppState.ModelArtifact request) throws IOException {
    return Collections.<String, Object>singletonMap("item", store.registerModel(request));
  }

  @PostMapping("/{id}/convert-rknn")
  public Map<String, Object> convertToRknn(@PathVariable String id,
                                           @RequestBody ConvertRknnRequest request) throws IOException {
    return Collections.<String, Object>singletonMap("item", store.convertModelToRknn(id, request.getTargetChip()));
  }

  public static class ConvertRknnRequest {
    private String targetChip;

    public String getTargetChip() {
      return targetChip;
    }

    public void setTargetChip(String targetChip) {
      this.targetChip = targetChip;
    }
  }
}
