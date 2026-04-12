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
    if (!sourceModelPath.isAbsolute()) {
      sourceModelPath = workspaceRoot.resolve(sourceModelPath).normalize();
    }

    model.setSourceModelPath(sourceModelPath.toString());
    model.setSourceModelFormat(extensionOf(sourceModelPath.getFileName().toString()));

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
      if ("rknn".equals(model.getSourceModelFormat())) {
        model.setPackageStatus("ready");
        model.setPackageMessage("训练产物已按默认 " + defaultVariant + " 格式直接打包完成。");
      } else {
        model.setPackageStatus("ready-non-rknn");
        model.setPackageMessage(
            "默认 " + defaultVariant + " 包结构已生成，但当前包内模型文件是 "
                + model.getSourceModelFormat()
                + "，如果目标运行时要求 RKNN，后续仍需替换为 .rknn。"
        );
      }
    } catch (IOException | IllegalStateException exception) {
      model.setPackageStatus("failed");
      model.setPackageMessage("默认算法包生成失败: " + exception.getMessage());
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

  private Path chooseSourceModelPath(AppState.ModelArtifact model, AppState.TrainingJob job) {
    List<Path> candidates = new ArrayList<Path>();
    if (StringUtils.hasText(job.getWeightsPath())) {
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
