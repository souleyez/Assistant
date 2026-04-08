package com.souleyez.assistant.controller;

import com.souleyez.assistant.domain.AppState;
import com.souleyez.assistant.service.AppStateStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final AppStateStore store;

  public ReportController(AppStateStore store) {
    this.store = store;
  }

  @GetMapping
  public Map<String, Object> reports() {
    AppState state = store.snapshot();
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("templates", state.getTemplates());
    response.put("outputRecords", state.getOutputRecords());
    return response;
  }

  @PostMapping("/template")
  public Map<String, Object> createTemplate(@RequestBody CreateTemplateRequest request) throws IOException {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", store.createTemplate(request.getLabel(), request.getDescription(), request.getSourceType()));
    return response;
  }

  @PostMapping(value = "/template-reference", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> uploadTemplateReference(@RequestParam("templateKey") String templateKey,
                                                     @RequestParam("file") MultipartFile file) throws IOException {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", store.addTemplateFile(templateKey, file));
    return response;
  }

  @PostMapping("/template-reference-link")
  public Map<String, Object> addTemplateReferenceLink(@RequestBody LinkReferenceRequest request) throws IOException {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", store.addTemplateLink(request.getTemplateKey(), request.getLabel(), request.getUrl()));
    return response;
  }

  @DeleteMapping("/template/{key}")
  public Map<String, Object> deleteTemplate(@PathVariable String key) throws IOException {
    store.deleteTemplate(key);
    return java.util.Collections.<String, Object>singletonMap("message", "deleted");
  }

  @DeleteMapping("/template-reference/{id}")
  public Map<String, Object> deleteTemplateReference(@PathVariable String id, @RequestParam("templateKey") String templateKey) throws IOException {
    store.deleteTemplateReference(templateKey, id);
    return java.util.Collections.<String, Object>singletonMap("message", "deleted");
  }

  @GetMapping("/template-reference/{id}/download")
  public ResponseEntity<Resource> downloadReference(@PathVariable String id, @RequestParam("templateKey") String templateKey) throws IOException {
    AppState.ReportReference reference = store.requireTemplateReference(templateKey, id);
    if (reference.getFilePath() == null) {
      return ResponseEntity.notFound().build();
    }
    Path path = Paths.get(reference.getFilePath());
    if (!Files.exists(path)) {
      return ResponseEntity.notFound().build();
    }
    Resource resource = new FileSystemResource(path);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reference.getName() + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(resource);
  }

  public static class CreateTemplateRequest {
    @NotBlank
    private String label;
    private String description;
    private String sourceType;

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getSourceType() {
      return sourceType;
    }

    public void setSourceType(String sourceType) {
      this.sourceType = sourceType;
    }
  }

  public static class LinkReferenceRequest {
    @NotBlank
    private String templateKey;
    private String label;
    @NotBlank
    private String url;

    public String getTemplateKey() {
      return templateKey;
    }

    public void setTemplateKey(String templateKey) {
      this.templateKey = templateKey;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }
}
