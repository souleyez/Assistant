package com.souleyez.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.souleyez.assistant.domain.AppState;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelPackagingService {
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final String defaultVariant;
  private final String configuredTemplatePath;
  private final double defaultConfThresh;

  public ModelPackagingService(
      @Value("${assistant.packaging.default-variant:m1}") String defaultVariant,
      @Value("${assistant.packaging.template-path:tools/rknn-package-converter/tests/fixtures/template_m1}") String configuredTemplatePath,
      @Value("${assistant.packaging.default-conf-thresh:0.25}") double defaultConfThresh
  ) {
    this.defaultVariant = defaultVariant;
    this.configuredTemplatePath = configuredTemplatePath;
    this.defaultConfThresh = defaultConfThresh;
  }

  public void packageTrainedModel(AppState.ModelArtifact model,
                                  AppState.TrainingProject project,
                                  AppState.Dataset dataset,
                                  AppState.TrainingJob job,
                                  String pythonCommand) {
    model.setPackageVariant(defaultVariant);
    Path workspaceRoot = resolveWorkspaceRoot();
    Path templatePath = resolveAgainstWorkspace(workspaceRoot, configuredTemplatePath);
    if (!Files.exists(templatePath)) {
      model.setPackageStatus("not-configured");
      model.setPackageMessage("默认算法包模板不存在: " + templatePath);
      return;
    }

    Path sourceModelPath = chooseSourceModelPath(model, job);
    if (sourceModelPath == null || !Files.exists(sourceModelPath)) {
      model.setPackageStatus("missing-source");
      model.setPackageMessage("未找到可打包的模型文件，至少需要 best.pt / best.onnx / best.rknn 之一。");
      return;
    }
    sourceModelPath = normalizeSourcePath(workspaceRoot, sourceModelPath);
    model.setSourceModelPath(sourceModelPath.toString());
    model.setSourceModelFormat(extensionOf(sourceModelPath.getFileName().toString()));
    model.setRknnStatus("pending");
    model.setRknnMessage("当前训练产物还不是 RKNN；如需 Rockchip 交付，请由操作员填写目标芯片后执行转换。");
    model.setRknnPath(null);

    packageModel(workspaceRoot, templatePath, model, project, dataset, sourceModelPath, pythonCommand);
  }

  public void convertModelToRknnAndPackage(AppState.ModelArtifact model,
                                           AppState.TrainingProject project,
                                           AppState.Dataset dataset,
                                           AppState.TrainingJob job,
                                           String pythonCommand,
                                           String targetChip) {
    if (!StringUtils.hasText(targetChip)) {
      throw new IllegalArgumentException("请先填写目标 Rockchip 芯片型号。");
    }
    model.setTargetChip(targetChip);
    Path workspaceRoot = resolveWorkspaceRoot();
    Path sourceModelPath = StringUtils.hasText(model.getSourceModelPath())
        ? Paths.get(model.getSourceModelPath())
        : chooseSourceModelPath(model, job);
    if (sourceModelPath == null) {
      model.setRknnStatus("missing-source");
      model.setRknnMessage("当前没有找到可转换的原始模型文件。");
      return;
    }
    sourceModelPath = normalizeSourcePath(workspaceRoot, sourceModelPath);
    if (!Files.exists(sourceModelPath)) {
      model.setRknnStatus("missing-source");
      model.setRknnMessage("原始模型文件不存在: " + sourceModelPath);
      return;
    }

    model.setSourceModelPath(sourceModelPath.toString());
    model.setSourceModelFormat(extensionOf(sourceModelPath.getFileName().toString()));

    try {
      Path rknnPath = sourceModelPath;
      String sourceFormat = model.getSourceModelFormat();
      if ("rknn".equals(sourceFormat)) {
        model.setRknnPath(rknnPath.toString());
        model.setRknnStatus("ready");
        model.setRknnMessage("模型已经是 RKNN，直接按默认格式打包。");
      } else if ("pt".equals(sourceFormat)) {
        int imageSize = project != null && project.getImageSize() > 0 ? project.getImageSize() : 640;
        rknnPath = exportRknnWithUltralytics(workspaceRoot, sourceModelPath, imageSize, model, pythonCommand, targetChip);
        model.setRknnPath(rknnPath.toString());
        model.setRknnStatus("ready");
        model.setRknnMessage("已按 " + targetChip + " 完成 RKNN 转换。");
      } else if ("onnx".equals(sourceFormat)) {
        rknnPath = exportRknnFromOnnx(workspaceRoot, sourceModelPath, model, pythonCommand, targetChip);
        model.setRknnPath(rknnPath.toString());
        model.setRknnStatus("ready");
        model.setRknnMessage("已按 " + targetChip + " 完成 RKNN 转换。");
      } else {
        model.setRknnStatus("unsupported-source");
        model.setRknnMessage("当前只支持从 PT、ONNX 或现成 RKNN 继续处理，当前源格式是 " + sourceFormat + "。");
        return;
      }

      model.setFilePath(rknnPath.toString());
      model.setExportFormat("rknn");
      if (project != null && dataset != null) {
        Path templatePath = resolveAgainstWorkspace(workspaceRoot, configuredTemplatePath);
        if (!Files.exists(templatePath)) {
          model.setPackageStatus("not-configured");
          model.setPackageMessage("默认算法包模板不存在: " + templatePath);
          return;
        }
        packageModel(workspaceRoot, templatePath, model, project, dataset, rknnPath, pythonCommand);
        if (!"failed".equals(model.getPackageStatus()) && !"not-configured".equals(model.getPackageStatus())) {
          model.setPackageStatus("ready");
          model.setPackageMessage("已完成 RKNN 转换，并按默认 " + defaultVariant + " 格式生成算法包。");
        }
      } else {
        model.setPackageStatus("metadata-required");
        model.setPackageMessage("已完成 RKNN 转换，但当前模型未关联训练数据集，暂不自动生成算法包。");
      }
    } catch (IOException exception) {
      model.setRknnStatus("failed");
      model.setRknnMessage("RKNN 转换失败: " + exception.getMessage());
    }
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

  private Path resolveAgainstWorkspace(Path workspaceRoot, String value) {
    Path path = Paths.get(value);
    if (!path.isAbsolute()) {
      path = workspaceRoot.resolve(path);
    }
    return path.normalize();
  }

  private Path normalizeSourcePath(Path workspaceRoot, Path sourceModelPath) {
    if (sourceModelPath.isAbsolute()) {
      return sourceModelPath.normalize();
    }
    Path currentRoot = Paths.get("").toAbsolutePath().normalize();
    Path currentCandidate = currentRoot.resolve(sourceModelPath).normalize();
    if (Files.exists(currentCandidate)) {
      return currentCandidate;
    }

    Path backendCandidate = workspaceRoot.resolve("backend").resolve(sourceModelPath).normalize();
    if (Files.exists(backendCandidate)) {
      return backendCandidate;
    }

    return workspaceRoot.resolve(sourceModelPath).normalize();
  }

  private Path chooseSourceModelPath(AppState.ModelArtifact model, AppState.TrainingJob job) {
    List<Path> candidates = new ArrayList<Path>();
    if (job != null && StringUtils.hasText(job.getWeightsPath())) {
      Path weightsPath = Paths.get(job.getWeightsPath());
      candidates.add(weightsPath.resolve("best.rknn"));
      candidates.add(weightsPath.resolve("best.onnx"));
      candidates.add(weightsPath.resolve("best.pt"));
    }
    if (StringUtils.hasText(model.getFilePath())) {
      Path modelPath = Paths.get(model.getFilePath());
      Path parent = modelPath.getParent();
      if (parent != null) {
        candidates.add(parent.resolve("best.rknn"));
        candidates.add(parent.resolve("best.onnx"));
        candidates.add(parent.resolve("best.pt"));
      }
      candidates.add(modelPath);
    }
    for (Path candidate : candidates) {
      if (candidate != null && Files.exists(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private Map<String, Object> buildManifest(Path templatePath,
                                            Path outputDir,
                                            Path outputArchive,
                                            String packageName,
                                            Path sourceModelPath,
                                            AppState.TrainingProject project,
                                            AppState.Dataset dataset,
                                            AppState.ModelArtifact model) {
    Map<String, Object> manifest = new LinkedHashMap<String, Object>();
    manifest.put("template_path", templatePath.toString());
    manifest.put("output_dir", outputDir.toString());
    manifest.put("output_archive", outputArchive.toString());
    manifest.put("package_name", packageName);
    manifest.put("variant", defaultVariant);

    Map<String, Object> engine = new LinkedHashMap<String, Object>();
    engine.put("name", slugify(project.getName()));
    engine.put("geid", deriveGeid(project, dataset));
    manifest.put("engine", engine);

    Map<String, Object> modelSpec = new LinkedHashMap<String, Object>();
    modelSpec.put("source_path", sourceModelPath.toString());
    modelSpec.put("package_path", "models/" + sourceModelPath.getFileName().toString());
    modelSpec.put("framework", resolveFramework(project.getYoloVersion()));
    manifest.put("model", modelSpec);

    List<Map<String, Object>> classes = new ArrayList<Map<String, Object>>();
    List<String> classNames = dataset.getClassNames();
    if (classNames == null || classNames.isEmpty()) {
      classNames = new ArrayList<String>();
      classNames.add("target");
    }
    for (int index = 0; index < classNames.size(); index++) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("id", index);
      item.put("name", classNames.get(index));
      item.put("display_name", classNames.get(index));
      item.put("enabled", Boolean.TRUE);
      classes.add(item);
    }
    manifest.put("classes", classes);
    manifest.put("conf_thresh", defaultConfThresh);
    manifest.put("json_overrides", new LinkedHashMap<String, Object>());
    return manifest;
  }

  private void packageModel(Path workspaceRoot,
                            Path templatePath,
                            AppState.ModelArtifact model,
                            AppState.TrainingProject project,
                            AppState.Dataset dataset,
                            Path sourceModelPath,
                            String pythonCommand) {
    String packageName = slugify(project.getName()) + "-" + defaultVariant;
    Path packageRoot = workspaceRoot.resolve(Paths.get("data", "runtime", "model-packages", model.getId()));
    Path outputDir = packageRoot.resolve(packageName);
    Path outputArchive = packageRoot.resolve(packageName + ".zip");
    Path manifestPath = packageRoot.resolve("manifest.json");

    try {
      Files.createDirectories(packageRoot);
      Map<String, Object> manifest = buildManifest(
          templatePath,
          outputDir,
          outputArchive,
          packageName,
          sourceModelPath,
          project,
          dataset,
          model
      );
      objectMapper.writeValue(manifestPath.toFile(), manifest);
      model.setPackageManifestPath(manifestPath.toString());

      runConverter(workspaceRoot, manifestPath, pythonCommand);

      model.setPackageDir(outputDir.toString());
      model.setPackageArchivePath(outputArchive.toString());
      if ("rknn".equals(extensionOf(sourceModelPath.getFileName().toString()))) {
        model.setPackageStatus("ready");
        model.setPackageMessage("训练产物已按默认 " + defaultVariant + " 格式直接打包完成。");
      } else {
        model.setPackageStatus("ready-non-rknn");
        model.setPackageMessage(
            "默认 " + defaultVariant + " 包结构已生成，但当前包内模型文件是 "
                + extensionOf(sourceModelPath.getFileName().toString())
                + "，如果目标运行时要求 RKNN，后续仍需替换为 .rknn。"
        );
      }
    } catch (IOException | IllegalStateException exception) {
      model.setPackageStatus("failed");
      model.setPackageMessage("默认算法包生成失败: " + exception.getMessage());
    }
  }

  private void runConverter(Path workspaceRoot, Path manifestPath, String pythonCommand) throws IOException {
    String effectivePython = StringUtils.hasText(pythonCommand) ? pythonCommand : "python";
    Path moduleRoot = workspaceRoot.resolve(Paths.get("tools", "rknn-package-converter", "src"));
    if (!Files.isDirectory(moduleRoot)) {
      throw new IOException("converter module path not found: " + moduleRoot);
    }
    ProcessBuilder builder = new ProcessBuilder(
        effectivePython,
        "-m",
        "rknn_package_converter",
        "build",
        manifestPath.toString()
    );
    builder.directory(workspaceRoot.toFile());
    builder.redirectErrorStream(true);
    Map<String, String> environment = builder.environment();
    String previousPythonPath = environment.get("PYTHONPATH");
    String joinedPythonPath = moduleRoot.toString();
    if (StringUtils.hasText(previousPythonPath)) {
      joinedPythonPath = moduleRoot + System.getProperty("path.separator") + previousPythonPath;
    }
    environment.put("PYTHONPATH", joinedPythonPath);

    Process process = builder.start();
    String output;
    try {
      output = readProcessOutput(process.getInputStream());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException(output.trim());
      }
      objectMapper.readValue(output, new TypeReference<Map<String, Object>>() { });
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("converter interrupted", exception);
    }
  }

  private Path exportRknnWithUltralytics(Path workspaceRoot,
                                         Path sourceModelPath,
                                         int imageSize,
                                         AppState.ModelArtifact model,
                                         String pythonCommand,
                                         String targetChip) throws IOException {
    String effectivePython = StringUtils.hasText(pythonCommand) ? pythonCommand : "python";
    Path exportRoot = workspaceRoot.resolve(Paths.get("data", "runtime", "rknn-exports", model.getId(), targetChip));
    Files.createDirectories(exportRoot);

    String script = String.join("\n", Arrays.asList(
        "from pathlib import Path",
        "from ultralytics import YOLO",
        "source = Path(r'" + normalizeForPython(sourceModelPath.toString()) + "')",
        "workdir = Path(r'" + normalizeForPython(exportRoot.toString()) + "')",
        "workdir.mkdir(parents=True, exist_ok=True)",
        "before = {str(item.resolve()) for item in workdir.rglob('*.rknn')}",
        "model = YOLO(str(source))",
        "model.export(format='rknn', name='" + normalizeForPython(targetChip) + "', imgsz=" + imageSize + ")",
        "after = [str(item.resolve()) for item in workdir.rglob('*.rknn') if str(item.resolve()) not in before]",
        "if not after:",
        "    after = [str(item.resolve()) for item in workdir.rglob('*.rknn')]",
        "if not after:",
        "    raise SystemExit('No RKNN file generated')",
        "print('RKNN_PATH=' + after[-1])"
    ));

    ProcessBuilder builder = new ProcessBuilder(effectivePython, "-c", script);
    builder.directory(exportRoot.toFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output;
    try {
      output = readProcessOutput(process.getInputStream());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException(output.trim());
      }
      for (String line : output.split("\\R")) {
        if (line.startsWith("RKNN_PATH=")) {
          Path exported = Paths.get(line.substring("RKNN_PATH=".length()).trim());
          if (Files.exists(exported)) {
            return exported;
          }
        }
      }
      throw new IOException("未从导出日志中解析到 RKNN 路径。");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("rknn export interrupted", exception);
    }
  }

  private Path exportRknnFromOnnx(Path workspaceRoot,
                                  Path sourceModelPath,
                                  AppState.ModelArtifact model,
                                  String pythonCommand,
                                  String targetChip) throws IOException {
    String effectivePython = StringUtils.hasText(pythonCommand) ? pythonCommand : "python";
    Path exportRoot = workspaceRoot.resolve(Paths.get("data", "runtime", "rknn-exports", model.getId(), targetChip));
    Files.createDirectories(exportRoot);
    String outputName = baseName(sourceModelPath.getFileName().toString()) + "-" + targetChip + ".rknn";

    String script = String.join("\n", Arrays.asList(
        "from pathlib import Path",
        "from rknn.api import RKNN",
        "source = Path(r'" + normalizeForPython(sourceModelPath.toString()) + "')",
        "workdir = Path(r'" + normalizeForPython(exportRoot.toString()) + "')",
        "output = workdir / '" + normalizeForPython(outputName) + "'",
        "workdir.mkdir(parents=True, exist_ok=True)",
        "rknn = RKNN(verbose=False)",
        "ret = rknn.config(target_platform='" + normalizeForPython(targetChip) + "')",
        "if ret != 0:",
        "    raise SystemExit('config failed: %s' % ret)",
        "ret = rknn.load_onnx(model=str(source))",
        "if ret != 0:",
        "    raise SystemExit('load_onnx failed: %s' % ret)",
        "ret = rknn.build(do_quantization=False)",
        "if ret != 0:",
        "    raise SystemExit('build failed: %s' % ret)",
        "ret = rknn.export_rknn(str(output))",
        "if ret != 0:",
        "    raise SystemExit('export_rknn failed: %s' % ret)",
        "print('RKNN_PATH=' + str(output.resolve()))",
        "rknn.release()"
    ));

    ProcessBuilder builder = new ProcessBuilder(effectivePython, "-c", script);
    builder.directory(exportRoot.toFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output;
    try {
      output = readProcessOutput(process.getInputStream());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException(output.trim());
      }
      for (String line : output.split("\\R")) {
        if (line.startsWith("RKNN_PATH=")) {
          Path exported = Paths.get(line.substring("RKNN_PATH=".length()).trim());
          if (Files.exists(exported)) {
            return exported;
          }
        }
      }
      throw new IOException("未从 ONNX 转换日志中解析到 RKNN 路径。");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("onnx to rknn export interrupted", exception);
    }
  }

  private int deriveGeid(AppState.TrainingProject project, AppState.Dataset dataset) {
    String seed = project.getName() + ":" + dataset.getId();
    return 100000 + Math.abs(seed.hashCode() % 900000);
  }

  private String resolveFramework(String yoloVersion) {
    String normalized = yoloVersion == null ? "" : yoloVersion.toLowerCase(Locale.ROOT);
    if (normalized.contains("5")) {
      return "yolov5";
    }
    return "yolov8";
  }

  private String slugify(String value) {
    String normalized = StringUtils.hasText(value) ? value : "model";
    String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    slug = slug.replaceAll("^_+|_+$", "");
    return StringUtils.hasText(slug) ? slug : "model";
  }

  private String extensionOf(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index >= 0 ? fileName.substring(index + 1).toLowerCase(Locale.ROOT) : "bin";
  }

  private String baseName(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index >= 0 ? fileName.substring(0, index) : fileName;
  }

  private String normalizeForPython(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  private String readProcessOutput(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = inputStream.read(buffer)) >= 0) {
      outputStream.write(buffer, 0, read);
    }
    return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
  }
}
