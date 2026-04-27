package com.souleyez.assistant.controller;

import com.souleyez.assistant.service.QuickStartService;
import java.io.IOException;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/quick-start")
public class QuickStartController {
  private final QuickStartService quickStartService;

  public QuickStartController(QuickStartService quickStartService) {
    this.quickStartService = quickStartService;
  }

  @GetMapping
  public Map<String, Object> summary() {
    return quickStartService.summary();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> create(
      @RequestParam("files") MultipartFile[] files,
      @RequestParam("targetDescription") @NotBlank String targetDescription,
      @RequestParam(value = "autoStart", defaultValue = "true") boolean autoStart
  ) throws IOException {
    return quickStartService.createQuickStart(files, targetDescription, autoStart);
  }

  @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> uploadChunk(
      @RequestParam("uploadId") @NotBlank String uploadId,
      @RequestParam("fileName") @NotBlank String fileName,
      @RequestParam("fileIndex") int fileIndex,
      @RequestParam("chunkIndex") int chunkIndex,
      @RequestParam("chunk") MultipartFile chunk
  ) throws IOException {
    return quickStartService.uploadChunk(uploadId, fileName, fileIndex, chunkIndex, chunk);
  }

  @PostMapping(value = "/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> completeChunkedUpload(
      @RequestParam("uploadId") @NotBlank String uploadId,
      @RequestParam("targetDescription") @NotBlank String targetDescription,
      @RequestParam(value = "autoStart", defaultValue = "true") boolean autoStart
  ) throws IOException {
    return quickStartService.completeChunkedQuickStart(uploadId, targetDescription, autoStart);
  }
}
