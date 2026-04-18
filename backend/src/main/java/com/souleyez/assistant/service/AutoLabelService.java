package com.souleyez.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AutoLabelService {
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final boolean enabled;
  private final String configuredModel;
  private final String fallbackModel;
  private final double confThreshold;
  private final double iouThreshold;
  private final int maxDetections;
  private final int defaultImageSize;

  public AutoLabelService(
      @Value("${assistant.autolabel.enabled:true}") boolean enabled,
      @Value("${assistant.autolabel.model:yolov8s-worldv2.pt}") String configuredModel,
      @Value("${assistant.autolabel.fallback-model:yolov8s-world.pt}") String fallbackModel,
      @Value("${assistant.autolabel.conf-threshold:0.20}") double confThreshold,
      @Value("${assistant.autolabel.iou-threshold:0.50}") double iouThreshold,
      @Value("${assistant.autolabel.max-detections:20}") int maxDetections,
      @Value("${assistant.autolabel.image-size:640}") int defaultImageSize
  ) {
    this.enabled = enabled;
    this.configuredModel = defaultText(configuredModel, "yolov8s-worldv2.pt");
    this.fallbackModel = defaultText(fallbackModel, "yolov8s-world.pt");
    this.confThreshold = confThreshold;
    this.iouThreshold = iouThreshold;
    this.maxDetections = maxDetections;
    this.defaultImageSize = defaultImageSize;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getConfiguredModel() {
    return configuredModel;
  }

  public double getConfThreshold() {
    return confThreshold;
  }

  public AutoLabelReport autoLabel(Path sessionRoot,
                                   Path datasetRoot,
                                   List<String> classNames,
                                   List<String> detectionPrompts,
                                   int imageSize) {
    AutoLabelReport skipped = new AutoLabelReport();
    skipped.setEnabled(enabled);
    skipped.setModel(configuredModel);
    skipped.setStatus("skipped");
    skipped.setMessage("自动预标注未启用。");
    if (!enabled) {
      return skipped;
    }
    if (classNames == null || classNames.isEmpty()) {
      skipped.setStatus("missing-classes");
      skipped.setMessage("Gemma 没有给出可用类别，自动预标注已跳过。");
      return skipped;
    }

    Path workspaceRoot = resolveWorkspaceRoot();
    Path autoLabelRoot = sessionRoot.resolve("autolabel");
    try {
      Files.createDirectories(autoLabelRoot);
      Path scriptPath = autoLabelRoot.resolve("auto_label_yolo_world.py");
      Path manifestPath = autoLabelRoot.resolve("auto-label-manifest.json");
      Path reportPath = autoLabelRoot.resolve("auto-label-report.json");

      writeAutoLabelScript(scriptPath);
      writeManifest(manifestPath, reportPath, datasetRoot, classNames, detectionPrompts, imageSize);

      String effectivePython = resolvePythonCommand(workspaceRoot);
      RunnerExecution execution = runAutoLabelProcess(workspaceRoot, effectivePython, scriptPath, manifestPath);
      AutoLabelReport report = readReport(reportPath);
      if (shouldRetryBootstrap(execution, report)) {
        Files.deleteIfExists(reportPath);
        execution = runAutoLabelProcess(workspaceRoot, effectivePython, scriptPath, manifestPath);
        report = readReport(reportPath);
      }
      report.setEnabled(true);
      if (StringUtils.hasText(execution.getOutput())) {
        report.setProcessOutput(execution.getOutput().trim());
      }
      if (execution.getExitCode() != 0 && !StringUtils.hasText(report.getMessage())) {
        report.setStatus("failed");
        report.setMessage(execution.getOutput().trim());
      }
      return report;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      skipped.setStatus("failed");
      skipped.setMessage("自动预标注被中断。");
      return skipped;
    } catch (IOException exception) {
      skipped.setStatus("failed");
      skipped.setMessage("自动预标注失败: " + exception.getMessage());
      return skipped;
    }
  }

  private void writeManifest(Path manifestPath,
                             Path reportPath,
                             Path datasetRoot,
                             List<String> classNames,
                             List<String> detectionPrompts,
                             int imageSize) throws IOException {
    Map<String, Object> manifest = new LinkedHashMap<String, Object>();
    manifest.put("datasetRoot", datasetRoot.toAbsolutePath().normalize().toString());
    manifest.put("reportPath", reportPath.toAbsolutePath().normalize().toString());
    manifest.put("classNames", classNames);
    manifest.put("detectionPrompts", normalizePrompts(classNames, detectionPrompts));
    manifest.put("modelCandidates", modelCandidates());
    manifest.put("confThreshold", confThreshold);
    manifest.put("iouThreshold", iouThreshold);
    manifest.put("maxDetections", maxDetections);
    manifest.put("imageSize", imageSize > 0 ? Math.max(imageSize, defaultImageSize) : defaultImageSize);
    objectMapper.writeValue(manifestPath.toFile(), manifest);
  }

  private List<String> normalizePrompts(List<String> classNames, List<String> detectionPrompts) {
    List<String> normalized = new ArrayList<String>();
    if (detectionPrompts != null) {
      for (String prompt : detectionPrompts) {
        if (StringUtils.hasText(prompt)) {
          normalized.add(prompt.trim());
        }
      }
    }
    if (normalized.isEmpty()) {
      normalized.addAll(classNames);
    }
    while (normalized.size() < classNames.size()) {
      normalized.add(classNames.get(normalized.size()));
    }
    if (normalized.size() > classNames.size()) {
      normalized = new ArrayList<String>(normalized.subList(0, classNames.size()));
    }
    return normalized;
  }

  private List<String> modelCandidates() {
    List<String> candidates = new ArrayList<String>();
    candidates.add(configuredModel);
    if (StringUtils.hasText(fallbackModel)
        && !fallbackModel.equals(configuredModel)) {
      candidates.add(fallbackModel);
    }
    return candidates;
  }

  private AutoLabelReport readReport(Path reportPath) throws IOException {
    if (!Files.exists(reportPath)) {
      AutoLabelReport report = new AutoLabelReport();
      report.setEnabled(true);
      report.setStatus("failed");
      report.setMessage("自动预标注没有生成结果报告。");
      return report;
    }
    return objectMapper.readValue(reportPath.toFile(), new TypeReference<AutoLabelReport>() { });
  }

  private RunnerExecution runAutoLabelProcess(Path workspaceRoot,
                                              String effectivePython,
                                              Path scriptPath,
                                              Path manifestPath) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(
        effectivePython,
        scriptPath.toString(),
        manifestPath.toString()
    );
    builder.directory(workspaceRoot.toFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = readProcessOutput(process.getInputStream());
    int exitCode = process.waitFor();
    return new RunnerExecution(exitCode, output);
  }

  private boolean shouldRetryBootstrap(RunnerExecution execution, AutoLabelReport report) {
    String combined = defaultText(execution.getOutput(), "") + "\n" + defaultText(report.getMessage(), "");
    String normalized = combined.toLowerCase(Locale.ROOT);
    if (!normalized.contains("autoupdate success")
        && !normalized.contains("restart runtime or rerun command")) {
      return false;
    }
    return execution.getExitCode() != 0
        || "failed".equals(report.getStatus())
        || !StringUtils.hasText(report.getModel());
  }

  private void writeAutoLabelScript(Path scriptPath) throws IOException {
    List<String> lines = Arrays.asList(
        "from pathlib import Path",
        "import json",
        "import sys",
        "from ultralytics import YOLOWorld",
        "",
        "IMAGE_SUFFIXES = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}",
        "",
        "def collect_targets(dataset_root):",
        "    targets = []",
        "    existing = 0",
        "    total = 0",
        "    for split in ('train', 'val', 'test'):",
        "        image_dir = dataset_root / split / 'images'",
        "        label_dir = dataset_root / split / 'labels'",
        "        if not image_dir.is_dir():",
        "            continue",
        "        label_dir.mkdir(parents=True, exist_ok=True)",
        "        for image in sorted(image_dir.rglob('*')):",
        "            if image.suffix.lower() not in IMAGE_SUFFIXES or not image.is_file():",
        "                continue",
        "            total += 1",
        "            label_path = label_dir / (image.stem + '.txt')",
        "            if label_path.exists() and label_path.stat().st_size > 0:",
        "                existing += 1",
        "                continue",
        "            targets.append({'split': split, 'image': image, 'label': label_path})",
        "    return total, existing, targets",
        "",
        "def load_model(model_candidates):",
        "    errors = []",
        "    for name in model_candidates:",
        "        try:",
        "            return YOLOWorld(name, verbose=False), name",
        "        except Exception as exc:",
        "            errors.append(f'{name}: {exc}')",
        "    raise RuntimeError(' ; '.join(errors) or 'No YOLOWorld model could be loaded')",
        "",
        "def write_report(report_path, payload):",
        "    report_path.parent.mkdir(parents=True, exist_ok=True)",
        "    report_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\\n', encoding='utf-8')",
        "",
        "def main(manifest_path):",
        "    manifest = json.loads(Path(manifest_path).read_text(encoding='utf-8'))",
        "    dataset_root = Path(manifest['datasetRoot'])",
        "    report_path = Path(manifest['reportPath'])",
        "    prompts = manifest.get('detectionPrompts') or manifest.get('classNames') or []",
        "    total, existing, targets = collect_targets(dataset_root)",
        "    report = {",
        "        'enabled': True,",
        "        'status': 'skipped',",
        "        'model': manifest.get('modelCandidates', [''])[0] if manifest.get('modelCandidates') else '',",
        "        'classNames': manifest.get('classNames', []),",
        "        'detectionPrompts': prompts,",
        "        'totalImageCount': total,",
        "        'existingLabelCount': existing,",
        "        'candidateImageCount': len(targets),",
        "        'createdLabelCount': 0,",
        "        'remainingImageCount': len(targets),",
        "        'boxCount': 0,",
        "        'averageConfidence': 0.0,",
        "        'message': '',",
        "        'sampleMissingImages': [],",
        "    }",
        "    try:",
        "        if not prompts:",
        "            report['status'] = 'missing-prompts'",
        "            report['message'] = 'No detection prompts available for auto labeling.'",
        "            write_report(report_path, report)",
        "            return 0",
        "        if not targets:",
        "            report['status'] = 'ready'",
        "            report['message'] = 'No unlabeled images were found. Dataset is already fully labeled.'",
        "            write_report(report_path, report)",
        "            return 0",
        "        model, loaded_name = load_model(manifest.get('modelCandidates') or ['yolov8s-worldv2.pt'])",
        "        report['model'] = loaded_name",
        "        model.set_classes(list(prompts))",
        "        total_conf = 0.0",
        "        for item in targets:",
        "            result = model.predict(",
        "                source=str(item['image']),",
        "                conf=float(manifest.get('confThreshold', 0.2)),",
        "                iou=float(manifest.get('iouThreshold', 0.5)),",
        "                imgsz=int(manifest.get('imageSize', 640)),",
        "                max_det=int(manifest.get('maxDetections', 20)),",
        "                verbose=False,",
        "            )[0]",
        "            lines = []",
        "            if getattr(result, 'boxes', None) is not None and len(result.boxes) > 0:",
        "                xywhn = result.boxes.xywhn.cpu().tolist()",
        "                cls_items = result.boxes.cls.cpu().tolist()",
        "                conf_items = result.boxes.conf.cpu().tolist()",
        "                for box, cls_idx, conf_score in zip(xywhn, cls_items, conf_items):",
        "                    idx = int(cls_idx)",
        "                    if idx < 0 or idx >= len(manifest.get('classNames', [])):",
        "                        continue",
        "                    x, y, w, h = box",
        "                    if w <= 0 or h <= 0:",
        "                        continue",
        "                    lines.append(f'{idx} {x:.6f} {y:.6f} {w:.6f} {h:.6f}')",
        "                    total_conf += float(conf_score)",
        "            if lines:",
        "                item['label'].write_text('\\n'.join(lines) + '\\n', encoding='utf-8')",
        "                report['createdLabelCount'] += 1",
        "                report['boxCount'] += len(lines)",
        "            elif len(report['sampleMissingImages']) < 12:",
        "                report['sampleMissingImages'].append(str(item['image']))",
        "        report['remainingImageCount'] = max(0, len(targets) - report['createdLabelCount'])",
        "        if report['boxCount'] > 0:",
        "            report['averageConfidence'] = round(total_conf / report['boxCount'], 4)",
        "        if report['createdLabelCount'] == len(targets):",
        "            report['status'] = 'ready'",
        "            report['message'] = 'Auto labeling covered every previously unlabeled image.'",
        "        elif report['createdLabelCount'] > 0:",
        "            report['status'] = 'partial'",
        "            report['message'] = 'Auto labeling covered part of the dataset. Manual review is still required.'",
        "        else:",
        "            report['status'] = 'empty'",
        "            report['message'] = 'Auto labeling did not produce reliable boxes.'",
        "        write_report(report_path, report)",
        "        return 0",
        "    except Exception as exc:",
        "        report['status'] = 'failed'",
        "        report['message'] = str(exc)",
        "        write_report(report_path, report)",
        "        return 1",
        "",
        "if __name__ == '__main__':",
        "    sys.exit(main(sys.argv[1]))"
    );
    Files.write(scriptPath, lines, StandardCharsets.UTF_8);
  }

  private Path resolveWorkspaceRoot() {
    Path current = Paths.get("").toAbsolutePath().normalize();
    if (Files.isDirectory(current.resolve("tools").resolve("rknn-package-converter"))) {
      return current;
    }
    Path parent = current.getParent();
    if (parent != null && Files.isDirectory(parent.resolve("tools").resolve("rknn-package-converter"))) {
      return parent;
    }
    return current;
  }

  private String resolvePythonCommand(Path workspaceRoot) {
    String envPython = System.getenv("YOLO_PYTHON");
    if (StringUtils.hasText(envPython)) {
      return envPython;
    }
    Path linuxVenv = workspaceRoot.resolve(Paths.get(".venv", "bin", "python"));
    if (Files.exists(linuxVenv)) {
      return linuxVenv.toString();
    }
    Path windowsVenv = workspaceRoot.resolve(Paths.get(".venv", "Scripts", "python.exe"));
    if (Files.exists(windowsVenv)) {
      return windowsVenv.toString();
    }
    return "python";
  }

  private String readProcessOutput(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, length);
    }
    return outputStream.toString(StandardCharsets.UTF_8.name());
  }

  private String defaultText(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  public static class AutoLabelReport {
    private boolean enabled;
    private String status;
    private String model;
    private List<String> classNames = new ArrayList<String>();
    private List<String> detectionPrompts = new ArrayList<String>();
    private int totalImageCount;
    private int existingLabelCount;
    private int candidateImageCount;
    private int createdLabelCount;
    private int remainingImageCount;
    private int boxCount;
    private double averageConfidence;
    private String message;
    private List<String> sampleMissingImages = new ArrayList<String>();
    private String processOutput;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public List<String> getClassNames() {
      return classNames;
    }

    public void setClassNames(List<String> classNames) {
      this.classNames = classNames;
    }

    public List<String> getDetectionPrompts() {
      return detectionPrompts;
    }

    public void setDetectionPrompts(List<String> detectionPrompts) {
      this.detectionPrompts = detectionPrompts;
    }

    public int getTotalImageCount() {
      return totalImageCount;
    }

    public void setTotalImageCount(int totalImageCount) {
      this.totalImageCount = totalImageCount;
    }

    public int getExistingLabelCount() {
      return existingLabelCount;
    }

    public void setExistingLabelCount(int existingLabelCount) {
      this.existingLabelCount = existingLabelCount;
    }

    public int getCandidateImageCount() {
      return candidateImageCount;
    }

    public void setCandidateImageCount(int candidateImageCount) {
      this.candidateImageCount = candidateImageCount;
    }

    public int getCreatedLabelCount() {
      return createdLabelCount;
    }

    public void setCreatedLabelCount(int createdLabelCount) {
      this.createdLabelCount = createdLabelCount;
    }

    public int getRemainingImageCount() {
      return remainingImageCount;
    }

    public void setRemainingImageCount(int remainingImageCount) {
      this.remainingImageCount = remainingImageCount;
    }

    public int getBoxCount() {
      return boxCount;
    }

    public void setBoxCount(int boxCount) {
      this.boxCount = boxCount;
    }

    public double getAverageConfidence() {
      return averageConfidence;
    }

    public void setAverageConfidence(double averageConfidence) {
      this.averageConfidence = averageConfidence;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public List<String> getSampleMissingImages() {
      return sampleMissingImages;
    }

    public void setSampleMissingImages(List<String> sampleMissingImages) {
      this.sampleMissingImages = sampleMissingImages;
    }

    public String getProcessOutput() {
      return processOutput;
    }

    public void setProcessOutput(String processOutput) {
      this.processOutput = processOutput;
    }

    public double coverage() {
      if (totalImageCount <= 0) {
        return 0.0d;
      }
      double covered = existingLabelCount + createdLabelCount;
      return covered / totalImageCount;
    }
  }

  private static class RunnerExecution {
    private final int exitCode;
    private final String output;

    private RunnerExecution(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }

    public int getExitCode() {
      return exitCode;
    }

    public String getOutput() {
      return output;
    }
  }
}
