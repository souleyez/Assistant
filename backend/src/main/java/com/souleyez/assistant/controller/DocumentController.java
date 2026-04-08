package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.io.IOException;
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
@RequestMapping("/api/documents")
public class DocumentController {
  private final AppStateStore store;

  public DocumentController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping
  public Map<String, Object> documents() {
    AppState state = store.snapshot();
    int parsed = 0;
    for (AppState.DocumentItem item : state.getDocuments()) {
      if ("parsed".equals(item.getParseStatus())) {
        parsed++;
      }
    }
    Map<String, Object> meta = new LinkedHashMap<String, Object>();
    meta.put("parsed", parsed);

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("items", state.getDocuments());
    response.put("libraries", state.getLibraries());
    response.put("meta", meta);
    response.put("totalFiles", state.getDocuments().size());
    return response;
  }

  @PostMapping("/libraries")
  public Map<String, Object> createLibrary(@RequestBody CreateLibraryRequest request) throws IOException {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", store.createLibrary(request.getName()));
    return response;
  }

  public static class CreateLibraryRequest {
    @NotBlank
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
