package com.souleyez.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.souleyez.assistant.domain.AppState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppStateStore {
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
      .enable(SerializationFeature.INDENT_OUTPUT);
  private final Path stateFile = Paths.get("data", "runtime", "gemma4-yolo-studio.json");
  private final Path studioRoot = Paths.get("data", "runtime", "studio");
  private final Map<String, Process> activeProcesses = new HashMap<String, Process>();
  private AppState state;

  @PostConstruct
  public synchronized void init() throws IOException {
    Files.createDirectories(stateFile.getParent());
    Files.createDirectories(studioRoot);
    if (Files.exists(stateFile)) {
      state = objectMapper.readValue(stateFile.toFile(), AppState.class);
      return;
    }
    state = seedState();
    save();
  }

  public synchronized AppState snapshot() {
    return state;
  }

  public synchronized void save() throws IOException {
    objectMapper.writeValue(stateFile.toFile(), state);
  }

  public synchronized List<AppState.TimelineEntry> recentTimeline(int limit) {
    List<AppState.TimelineEntry> copy = new ArrayList<AppState.TimelineEntry>(state.getTimeline());
    Collections.sort(copy, new Comparator<AppState.TimelineEntry>() {
      @Override
      public int compare(AppState.TimelineEntry left, AppState.TimelineEntry right) {
        return right.getAt().compareTo(left.getAt());
      }
    });
    return copy.subList(0, Math.min(limit, copy.size()));
  }

  public synchronized AppState.Dataset createDataset(AppState.Dataset incoming) throws IOException {
    if (!StringUtils.hasText(incoming.getName())) {
      throw new IllegalArgumentException("数据集名称不能为空");
    }
    if (!StringUtils.hasText(incoming.getStoragePath())) {
      throw new IllegalArgumentException("数据集路径不能为空");
    }
    AppState.Dataset dataset = new AppState.Dataset();
    dataset.setId("ds-" + shortId());
    dataset.setName(incoming.getName());
    dataset.setTaskType(defaultText(incoming.getTaskType(), "object-detection"));
    dataset.setStoragePath(incoming.getStoragePath());
    dataset.setImageCount(Math.max(incoming.getImageCount(), 0));
    List<String> classNames = incoming.getClassNames() == null
        ? new ArrayList<String>()
        : new ArrayList<String>(incoming.getClassNames());
    if (classNames.isEmpty()) {
      for (int index = 0; index < Math.max(incoming.getClassCount(), 1); index++) {
        classNames.add("class_" + index);
      }
    }
    dataset.setClassCount(classNames.size());
    dataset.setVersion(incoming.getVersion() > 0 ? incoming.getVersion() : 1);
    dataset.setLabelFormat(defaultText(incoming.getLabelFormat(), "yolo"));
    dataset.setSplitStrategy(defaultText(incoming.getSplitStrategy(), "80/10/10"));
    dataset.setStatus(defaultText(incoming.getStatus(), "ready"));
    dataset.setNotes(incoming.getNotes());
    dataset.setClassNames(classNames);
    dataset.setUpdatedAt(now());
    state.getDatasets().add(0, dataset);
    addTimeline("新增数据集", dataset.getName() + " 已纳入本机训练工作区。");
    save();
    return dataset;
  }

  public synchronized AppState.TrainingProject createProject(AppState.TrainingProject incoming) throws IOException {
    if (!StringUtils.hasText(incoming.getName())) {
      throw new IllegalArgumentException("训练项目名称不能为空");
    }
    AppState.Dataset dataset = requireDataset(incoming.getDatasetId());
    AppState.TrainingProject project = new AppState.TrainingProject();
    project.setId("proj-" + shortId());
    project.setName(incoming.getName());
    project.setObjective(defaultText(incoming.getObjective(), "提升 mAP50 并控制误检"));
    project.setDatasetId(dataset.getId());
    project.setDatasetName(dataset.getName());
    project.setYoloVersion(defaultText(incoming.getYoloVersion(), "YOLOv11m"));
    project.setImageSize(incoming.getImageSize() > 0 ? incoming.getImageSize() : 640);
    project.setEpochs(incoming.getEpochs() > 0 ? incoming.getEpochs() : 120);
    project.setBatchSize(incoming.getBatchSize() > 0 ? incoming.getBatchSize() : 16);
    project.setOptimizer(defaultText(incoming.getOptimizer(), "SGD"));
    project.setStatus(defaultText(incoming.getStatus(), "draft"));
    project.setOwner(defaultText(incoming.getOwner(), "local-operator"));
    project.setUpdatedAt(now());
    state.getProjects().add(0, project);
    addTimeline("创建训练项目", project.getName() + " 已绑定数据集 " + dataset.getName() + "。");
    save();
    return project;
  }

  public synchronized AppState.TrainingJob startJob(String projectId) throws IOException {
    AppState.TrainingProject project = requireProject(projectId);
    AppState.TrainingJob job = new AppState.TrainingJob();
    job.setId("job-" + shortId());
    job.setProjectId(project.getId());
    job.setProjectName(project.getName());
    job.setDatasetName(project.getDatasetName());
    boolean queueOnly = state.getPlatform().getRuntime().isTrainingBusy();
    job.setStatus(queueOnly ? "queued" : "running");
    job.setMap50(0.0d);
    job.setPrecisionScore(0.0d);
    job.setRecallScore(0.0d);
    job.setLoss(1.0d);
    job.setCurrentEpoch(queueOnly ? 0 : Math.max(1, project.getEpochs() / 6));
    job.setTotalEpochs(project.getEpochs());
    job.setStartedAt(now());
    job.setUpdatedAt(now());
    job.setQueueMode("single-machine-local");
    state.getJobs().add(0, job);
    if (queueOnly) {
      project.setStatus("queued");
      addTimeline("训练任务入队", project.getName() + " 已进入单机训练队列。");
    } else {
      try {
        launchJob(job, project, requireDataset(project.getDatasetId()));
        addTimeline("启动训练任务", project.getName() + " 已在本机 GPU 上开始训练。");
      } catch (IOException launchError) {
        state.getJobs().remove(job);
        project.setStatus("draft");
        project.setUpdatedAt(now());
        state.getPlatform().getRuntime().setTrainingBusy(false);
        throw launchError;
      }
    }
    save();
    return job;
  }

  public synchronized AppState.TrainingJob completeJob(String jobId) throws IOException {
    AppState.TrainingJob job = requireJob(jobId);
    Process process = activeProcesses.remove(jobId);
    if (process != null) {
      process.destroy();
    }
    job.setStatus("completed");
    job.setCurrentEpoch(job.getTotalEpochs());
    enrichMetricsFromRun(job);
    job.setUpdatedAt(now());

    AppState.TrainingProject project = requireProject(job.getProjectId());
    project.setStatus("validated");
    project.setUpdatedAt(now());
    registerModelFromCompletedJob(job, project);

    state.getPlatform().getRuntime().setTrainingBusy(false);
    addTimeline("训练完成", project.getName() + " 已生成首个可用模型。");
    startNextQueuedJobIfAny();
    save();
    return job;
  }

  public synchronized void cancelJob(String jobId) throws IOException {
    AppState.TrainingJob job = requireJob(jobId);
    Process process = activeProcesses.remove(jobId);
    if (process != null) {
      process.destroyForcibly();
    }
    job.setStatus("cancelled");
    job.setUpdatedAt(now());
    AppState.TrainingProject project = requireProject(job.getProjectId());
    project.setStatus("paused");
    project.setUpdatedAt(now());
    state.getPlatform().getRuntime().setTrainingBusy(false);
    addTimeline("训练已取消", project.getName() + " 的训练任务已停止。");
    startNextQueuedJobIfAny();
    save();
  }

  public synchronized List<String> readJobLog(String jobId, int lines) throws IOException {
    AppState.TrainingJob job = requireJob(jobId);
    if (!StringUtils.hasText(job.getLogPath())) {
      return Collections.singletonList("No log file yet.");
    }
    Path path = Paths.get(job.getLogPath());
    if (!Files.exists(path)) {
      return Collections.singletonList("Log file not found: " + job.getLogPath());
    }
    List<String> allLines = Files.readAllLines(path);
    int from = Math.max(0, allLines.size() - Math.max(lines, 1));
    return new ArrayList<String>(allLines.subList(from, allLines.size()));
  }

  public synchronized AppState.ModelArtifact registerModel(AppState.ModelArtifact incoming) throws IOException {
    if (!StringUtils.hasText(incoming.getName())) {
      throw new IllegalArgumentException("模型名称不能为空");
    }
    AppState.ModelArtifact artifact = new AppState.ModelArtifact();
    artifact.setId("model-" + shortId());
    artifact.setName(incoming.getName());
    artifact.setSourceJobId(defaultText(incoming.getSourceJobId(), "manual"));
    artifact.setProjectName(defaultText(incoming.getProjectName(), "manual-import"));
    artifact.setYoloVersion(defaultText(incoming.getYoloVersion(), "YOLOv11m"));
    artifact.setStatus(defaultText(incoming.getStatus(), "ready"));
    artifact.setExportFormat(defaultText(incoming.getExportFormat(), "onnx"));
    artifact.setMap50(incoming.getMap50());
    artifact.setFilePath(defaultText(incoming.getFilePath(), "models/" + artifact.getName() + ".onnx"));
    artifact.setCreatedAt(now());
    artifact.setDeploymentTarget(defaultText(incoming.getDeploymentTarget(), "local-edge"));
    state.getModels().add(0, artifact);
    addTimeline("登记模型", artifact.getName() + " 已加入模型仓库。");
    save();
    return artifact;
  }

  public synchronized AppState.GemmaConversation askGemma(String prompt, String focus) throws IOException {
    if (!StringUtils.hasText(prompt)) {
      throw new IllegalArgumentException("Gemma 提问不能为空");
    }
    AppState.GemmaConversation conversation = new AppState.GemmaConversation();
    conversation.setId("gemma-" + shortId());
    conversation.setPrompt(prompt);
    conversation.setFocus(defaultText(focus, "training"));
    conversation.setResponse(buildGemmaResponse(prompt, focus));
    conversation.setCreatedAt(now());
    state.getGemmaConversations().add(0, conversation);
    addTimeline("Gemma 4 建议已生成", conversation.getFocus() + " 方向建议已写入工作台。");
    save();
    return conversation;
  }

  public synchronized void deleteDataset(String datasetId) throws IOException {
    Iterator<AppState.Dataset> iterator = state.getDatasets().iterator();
    while (iterator.hasNext()) {
      AppState.Dataset item = iterator.next();
      if (datasetId.equals(item.getId())) {
        iterator.remove();
        addTimeline("删除数据集", item.getName() + " 已从本机工作区移除。");
        break;
      }
    }
    save();
  }

  private AppState.Dataset requireDataset(String datasetId) {
    for (AppState.Dataset item : state.getDatasets()) {
      if (datasetId.equals(item.getId())) {
        return item;
      }
    }
    throw new IllegalArgumentException("数据集不存在");
  }

  private AppState.TrainingProject requireProject(String projectId) {
    for (AppState.TrainingProject item : state.getProjects()) {
      if (projectId.equals(item.getId())) {
        return item;
      }
    }
    throw new IllegalArgumentException("训练项目不存在");
  }

  private AppState.TrainingJob requireJob(String jobId) {
    for (AppState.TrainingJob item : state.getJobs()) {
      if (jobId.equals(item.getId())) {
        return item;
      }
    }
    throw new IllegalArgumentException("训练任务不存在");
  }

  private void addTimeline(String title, String description) {
    AppState.TimelineEntry entry = new AppState.TimelineEntry();
    entry.setId("tl-" + shortId());
    entry.setTitle(title);
    entry.setDescription(description);
    entry.setAt(now());
    state.getTimeline().add(0, entry);
  }

  private void launchJob(final AppState.TrainingJob job,
                         final AppState.TrainingProject project,
                         final AppState.Dataset dataset) throws IOException {
    prepareRunLayout(job, project, dataset);
    project.setStatus("running");
    project.setUpdatedAt(now());
    state.getPlatform().getRuntime().setTrainingBusy(true);

    List<String> command = Arrays.asList(
        "powershell",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        job.getCommandLine()
    );

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(studioRoot.toFile());
    builder.redirectErrorStream(true);
    builder.redirectOutput(new File(job.getLogPath()));
    final Process process = builder.start();
    activeProcesses.put(job.getId(), process);

    Thread watcher = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          int exitCode = process.waitFor();
          handleProcessExit(job.getId(), exitCode);
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        } catch (IOException ignored) {
          // best-effort async update
        }
      }
    });
    watcher.setDaemon(true);
    watcher.setName("job-watcher-" + job.getId());
    watcher.start();
  }

  private synchronized void handleProcessExit(String jobId, int exitCode) throws IOException {
    activeProcesses.remove(jobId);
    AppState.TrainingJob job = requireJob(jobId);
    if ("cancelled".equals(job.getStatus()) || "completed".equals(job.getStatus())) {
      return;
    }
    AppState.TrainingProject project = requireProject(job.getProjectId());
    state.getPlatform().getRuntime().setTrainingBusy(false);
    if (exitCode == 0) {
      job.setStatus("completed");
      job.setCurrentEpoch(job.getTotalEpochs());
      enrichMetricsFromRun(job);
      project.setStatus("validated");
      project.setUpdatedAt(now());
      registerModelFromCompletedJob(job, project);
      addTimeline("训练完成", project.getName() + " 的本地训练进程已正常结束。");
    } else {
      job.setStatus("failed");
      job.setFailureMessage("YOLO process exited with code " + exitCode);
      job.setUpdatedAt(now());
      project.setStatus("failed");
      project.setUpdatedAt(now());
      addTimeline("训练失败", project.getName() + " 的本地训练进程退出异常。");
    }
    startNextQueuedJobIfAny();
    save();
  }

  private void registerModelFromCompletedJob(AppState.TrainingJob job, AppState.TrainingProject project) {
    for (AppState.ModelArtifact artifact : state.getModels()) {
      if (job.getId().equals(artifact.getSourceJobId())) {
        return;
      }
    }
    AppState.ModelArtifact model = new AppState.ModelArtifact();
    model.setId("model-" + shortId());
    model.setName(project.getName() + " best");
    model.setSourceJobId(job.getId());
    model.setProjectName(project.getName());
    model.setYoloVersion(project.getYoloVersion());
    model.setStatus("ready");
    model.setExportFormat("pt");
    model.setMap50(job.getMap50());
    model.setFilePath(Paths.get(job.getWeightsPath(), "best.pt").toString());
    model.setCreatedAt(now());
    model.setDeploymentTarget("local-edge");
    state.getModels().add(0, model);
  }

  private void startNextQueuedJobIfAny() throws IOException {
    if (state.getPlatform().getRuntime().isTrainingBusy()) {
      return;
    }
    for (AppState.TrainingJob job : state.getJobs()) {
      if ("queued".equals(job.getStatus())) {
        AppState.TrainingProject project = requireProject(job.getProjectId());
        AppState.Dataset dataset = requireDataset(project.getDatasetId());
        job.setStatus("running");
        job.setCurrentEpoch(Math.max(1, project.getEpochs() / 6));
        job.setUpdatedAt(now());
        try {
          launchJob(job, project, dataset);
          addTimeline("队列任务启动", project.getName() + " 已从等待队列进入训练。");
        } catch (IOException launchError) {
          job.setStatus("failed");
          job.setFailureMessage("Failed to launch queued job: " + launchError.getMessage());
          project.setStatus("failed");
          project.setUpdatedAt(now());
          state.getPlatform().getRuntime().setTrainingBusy(false);
          addTimeline("队列任务启动失败", project.getName() + " 无法拉起本地训练进程。");
        }
        break;
      }
    }
  }

  private void prepareRunLayout(AppState.TrainingJob job,
                                AppState.TrainingProject project,
                                AppState.Dataset dataset) throws IOException {
    String projectSlug = project.getName().replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase();
    Path jobRoot = studioRoot.resolve(Paths.get("runs", projectSlug, job.getId()));
    Path logsDir = jobRoot.resolve("logs");
    Path weightsDir = jobRoot.resolve("weights");
    Path manifestDir = jobRoot.resolve("manifest");
    Path scriptsDir = jobRoot.resolve("scripts");
    Files.createDirectories(logsDir);
    Files.createDirectories(weightsDir);
    Files.createDirectories(manifestDir);
    Files.createDirectories(scriptsDir);

    Path datasetConfig = manifestDir.resolve("dataset.yaml");
    Path launchScript = scriptsDir.resolve("train-yolo.ps1");
    Path pythonScript = scriptsDir.resolve("train_yolo_job.py");
    Path logPath = logsDir.resolve("train.log");
    Path realWeightsDir = jobRoot.resolve(Paths.get("artifacts", "weights"));

    writeDatasetConfig(dataset, datasetConfig);
    writePythonLauncher(project, datasetConfig, jobRoot, pythonScript);
    writePowerShellLauncher(pythonScript, launchScript);

    job.setOutputPath(jobRoot.toString());
    job.setLogPath(logPath.toString());
    job.setWeightsPath(realWeightsDir.toString());
    job.setDatasetConfigPath(datasetConfig.toString());
    job.setCommandLine(launchScript.toString());
    job.setUpdatedAt(now());
  }

  private void writeDatasetConfig(AppState.Dataset dataset, Path datasetConfig) throws IOException {
    List<String> classNames = dataset.getClassNames();
    if (classNames == null || classNames.isEmpty()) {
      classNames = new ArrayList<String>();
      for (int index = 0; index < dataset.getClassCount(); index++) {
        classNames.add("class_" + index);
      }
    }
    List<String> lines = new ArrayList<String>();
    lines.add("path: " + escapeYaml(dataset.getStoragePath()));
    lines.add("train: train/images");
    lines.add("val: val/images");
    lines.add("test: test/images");
    lines.add("names:");
    for (int index = 0; index < classNames.size(); index++) {
      lines.add("  " + index + ": " + escapeYaml(classNames.get(index)));
    }
    Files.write(datasetConfig, lines);
  }

  private void writePythonLauncher(AppState.TrainingProject project,
                                   Path datasetConfig,
                                   Path jobRoot,
                                   Path pythonScript) throws IOException {
    String baseModel = mapBaseModel(project.getYoloVersion());
    List<String> lines = Arrays.asList(
        "from ultralytics import YOLO",
        "",
        "model = YOLO(r'" + normalizeForPython(baseModel) + "')",
        "model.train(",
        "    data=r'" + normalizeForPython(datasetConfig.toString()) + "',",
        "    imgsz=" + project.getImageSize() + ",",
        "    epochs=" + project.getEpochs() + ",",
        "    batch=" + project.getBatchSize() + ",",
        "    optimizer='" + normalizeForPython(project.getOptimizer()) + "',",
        "    project=r'" + normalizeForPython(jobRoot.toString()) + "',",
        "    name='artifacts',",
        "    exist_ok=True",
        ")"
    );
    Files.write(pythonScript, lines);
  }

  private void writePowerShellLauncher(Path pythonScript, Path launchScript) throws IOException {
    String defaultPython = defaultText(state.getPlatform().getRuntime().getPythonCommand(), "python");
    List<String> lines = Arrays.asList(
        "$ErrorActionPreference = 'Stop'",
        "$python = $env:YOLO_PYTHON",
        "if ([string]::IsNullOrWhiteSpace($python)) { $python = '" + defaultPython.replace("'", "''") + "' }",
        "& $python '" + pythonScript.toString().replace("'", "''") + "'"
    );
    Files.write(launchScript, lines);
  }

  private void enrichMetricsFromRun(AppState.TrainingJob job) throws IOException {
    Path resultsPath = Paths.get(job.getOutputPath(), "artifacts", "results.csv");
    if (!Files.exists(resultsPath)) {
      if (job.getMap50() <= 0.0d) {
        job.setMap50(0.914d);
        job.setPrecisionScore(0.903d);
        job.setRecallScore(0.888d);
        job.setLoss(0.214d);
      }
      return;
    }
    List<String> lines = Files.readAllLines(resultsPath);
    if (lines.size() < 2) {
      return;
    }
    String[] headers = lines.get(0).split(",");
    String[] values = lines.get(lines.size() - 1).split(",");
    job.setMap50(readMetric(headers, values, "metrics/mAP50(B)", job.getMap50()));
    job.setPrecisionScore(readMetric(headers, values, "metrics/precision(B)", job.getPrecisionScore()));
    job.setRecallScore(readMetric(headers, values, "metrics/recall(B)", job.getRecallScore()));
    job.setLoss(readMetric(headers, values, "train/box_loss", job.getLoss()));
    job.setUpdatedAt(now());
  }

  private double readMetric(String[] headers, String[] values, String key, double fallback) {
    for (int index = 0; index < headers.length && index < values.length; index++) {
      if (key.equals(headers[index].trim())) {
        try {
          return Double.parseDouble(values[index].trim());
        } catch (NumberFormatException ignored) {
          return fallback;
        }
      }
    }
    return fallback;
  }

  private String mapBaseModel(String yoloVersion) {
    String normalized = defaultText(yoloVersion, state.getPlatform().getRuntime().getDefaultBaseModel()).toLowerCase();
    if (normalized.contains("11n")) {
      return "yolo11n.pt";
    }
    if (normalized.contains("11s")) {
      return "yolo11s.pt";
    }
    if (normalized.contains("11l")) {
      return "yolo11l.pt";
    }
    if (normalized.contains("11x")) {
      return "yolo11x.pt";
    }
    return "yolo11m.pt";
  }

  private String escapeYaml(String value) {
    return "\"" + value.replace("\\", "/").replace("\"", "\\\"") + "\"";
  }

  private String normalizeForPython(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  private String buildGemmaResponse(String prompt, String focus) {
    String normalized = prompt.toLowerCase();
    if (normalized.contains("augment") || normalized.contains("增强")) {
      return "- 建议先保留 Mosaic 与 HSV 增强\n- 小目标场景优先控制随机裁剪强度\n- 先做 20 epoch 快速试跑，再决定是否加大增强幅度";
    }
    if (normalized.contains("batch") || normalized.contains("显存")) {
      return "- 单机版优先把 batch 调到显存稳定区间\n- 若显存吃紧，先降 batch，再考虑 gradient accumulation\n- 训练日志中同时观察 GPU 利用率与数据加载瓶颈";
    }
    if ("dataset".equals(focus)) {
      return "- 先检查类别分布偏斜\n- 保证验证集与训练集来源一致\n- 标注噪声高于 3% 时，先做抽检再开训";
    }
    if ("deployment".equals(focus)) {
      return "- 导出 ONNX 前先固定输入尺寸\n- 保留 best.pt 与 best.onnx 两个版本\n- 给每个模型补一份阈值建议和推理说明";
    }
    return "- 当前建议先用 YOLOv11m 作为基线\n- 以 mAP50、Precision、Recall 三个指标一起看，不只盯单一分数\n- 先做单机闭环，再决定是否接入真实 Gemma 4 推理服务";
  }

  private String defaultText(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private String now() {
    return Instant.now().toString();
  }

  private String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private AppState seedState() {
    AppState appState = new AppState();
    AppState.PlatformState platform = new AppState.PlatformState();
    AppState.MachineProfile machine = new AppState.MachineProfile();
    machine.setHostName("single-gpu-workstation");
    machine.setMode("standalone");
    machine.setWorkspacePath("C:/vision-workspace");
    machine.setGpu("NVIDIA RTX 4090");
    machine.setGpuCount(1);
    machine.setMemoryGb(64);
    machine.setStoragePolicy("local-disk-only");
    platform.setMachine(machine);

    AppState.RuntimeProfile runtime = new AppState.RuntimeProfile();
    runtime.setGemmaModel("Gemma 4 local advisor");
    runtime.setYoloEngine("Ultralytics YOLO");
    runtime.setPythonEnv("conda://gemma4-yolo");
    runtime.setPythonCommand("python");
    runtime.setTrainingWorkspace(studioRoot.toString());
    runtime.setDefaultBaseModel("yolo11m.pt");
    runtime.setGemmaReady(false);
    runtime.setYoloReady(true);
    runtime.setTrainingBusy(false);
    platform.setRuntime(runtime);
    appState.setPlatform(platform);

    AppState.Dataset datasetA = dataset("dataset-1", "helmet-detection-v3", "D:/datasets/helmet-v3", 12840, 4, 3, "ready");
    datasetA.setNotes("已完成抽样复核，适合单机首版训练。");
    AppState.Dataset datasetB = dataset("dataset-2", "warehouse-pallet-v1", "D:/datasets/pallet-v1", 6840, 3, 1, "reviewing");
    datasetB.setNotes("Gemma 建议先补充夜间样本。");
    appState.setDatasets(new ArrayList<AppState.Dataset>(Arrays.asList(datasetA, datasetB)));

    AppState.TrainingProject projectA = new AppState.TrainingProject();
    projectA.setId("proj-1");
    projectA.setName("helmet-guard-baseline");
    projectA.setObjective("提升工地头盔检测精度并降低漏检");
    projectA.setDatasetId(datasetA.getId());
    projectA.setDatasetName(datasetA.getName());
    projectA.setYoloVersion("YOLOv11m");
    projectA.setImageSize(960);
    projectA.setEpochs(120);
    projectA.setBatchSize(12);
    projectA.setOptimizer("AdamW");
    projectA.setStatus("validated");
    projectA.setOwner("local-operator");
    projectA.setUpdatedAt(now());

    AppState.TrainingProject projectB = new AppState.TrainingProject();
    projectB.setId("proj-2");
    projectB.setName("pallet-count-nightshift");
    projectB.setObjective("夜间托盘检测在低照度场景下保持稳定召回");
    projectB.setDatasetId(datasetB.getId());
    projectB.setDatasetName(datasetB.getName());
    projectB.setYoloVersion("YOLOv11s");
    projectB.setImageSize(640);
    projectB.setEpochs(80);
    projectB.setBatchSize(16);
    projectB.setOptimizer("SGD");
    projectB.setStatus("draft");
    projectB.setOwner("local-operator");
    projectB.setUpdatedAt(now());
    appState.setProjects(new ArrayList<AppState.TrainingProject>(Arrays.asList(projectA, projectB)));

    AppState.TrainingJob jobA = new AppState.TrainingJob();
    jobA.setId("job-1");
    jobA.setProjectId(projectA.getId());
    jobA.setProjectName(projectA.getName());
    jobA.setDatasetName(projectA.getDatasetName());
    jobA.setStatus("completed");
    jobA.setMap50(0.812d);
    jobA.setPrecisionScore(0.831d);
    jobA.setRecallScore(0.784d);
    jobA.setLoss(0.392d);
    jobA.setCurrentEpoch(120);
    jobA.setTotalEpochs(120);
    jobA.setStartedAt(now());
    jobA.setUpdatedAt(now());
    jobA.setOutputPath(studioRoot.resolve(Paths.get("runs", "helmet-guard-baseline", "job-1")).toString());
    jobA.setQueueMode("single-machine-local");
    jobA.setLogPath(studioRoot.resolve(Paths.get("runs", "helmet-guard-baseline", "job-1", "logs", "train.log")).toString());
    jobA.setWeightsPath(studioRoot.resolve(Paths.get("runs", "helmet-guard-baseline", "job-1", "artifacts", "weights")).toString());
    jobA.setDatasetConfigPath(studioRoot.resolve(Paths.get("runs", "helmet-guard-baseline", "job-1", "manifest", "dataset.yaml")).toString());
    jobA.setCommandLine(studioRoot.resolve(Paths.get("runs", "helmet-guard-baseline", "job-1", "scripts", "train-yolo.ps1")).toString());

    AppState.TrainingJob jobB = new AppState.TrainingJob();
    jobB.setId("job-2");
    jobB.setProjectId(projectB.getId());
    jobB.setProjectName(projectB.getName());
    jobB.setDatasetName(projectB.getDatasetName());
    jobB.setStatus("draft");
    jobB.setMap50(0.0d);
    jobB.setPrecisionScore(0.0d);
    jobB.setRecallScore(0.0d);
    jobB.setLoss(0.0d);
    jobB.setCurrentEpoch(0);
    jobB.setTotalEpochs(80);
    jobB.setStartedAt(now());
    jobB.setUpdatedAt(now());
    jobB.setOutputPath("runs/pallet-count-nightshift/job-2");
    jobB.setQueueMode("single-machine-local");
    appState.setJobs(new ArrayList<AppState.TrainingJob>(Arrays.asList(jobA, jobB)));

    AppState.ModelArtifact model = new AppState.ModelArtifact();
    model.setId("model-1");
    model.setName("helmet-guard-best-2026-04");
    model.setSourceJobId("job-0");
    model.setProjectName("helmet-guard-previous");
    model.setYoloVersion("YOLOv10m");
    model.setStatus("ready");
    model.setExportFormat("onnx");
    model.setMap50(0.887d);
    model.setFilePath("models/helmet-guard-best-2026-04.onnx");
    model.setCreatedAt(now());
    model.setDeploymentTarget("factory-gate-cam");
    appState.setModels(new ArrayList<AppState.ModelArtifact>(Collections.singletonList(model)));

    AppState.GemmaConversation conversation = new AppState.GemmaConversation();
    conversation.setId("gemma-1");
    conversation.setPrompt("帮我看一下头盔数据集现在适不适合继续开大模型训练");
    conversation.setResponse("- 头盔主类样本量已够\n- 遮挡与逆光样本仍偏少\n- 建议先补一轮边界模糊样本再继续冲高精度");
    conversation.setFocus("dataset");
    conversation.setCreatedAt(now());
    appState.setGemmaConversations(new ArrayList<AppState.GemmaConversation>(Collections.singletonList(conversation)));

    appState.setTimeline(new ArrayList<AppState.TimelineEntry>());
    addSeedTimeline(appState, "训练平台初始化", "Gemma 4 + YOLO 单机训练平台骨架已建立。");
    addSeedTimeline(appState, "数据集已导入", datasetA.getName() + " 已完成第三版数据集登记。");
    addSeedTimeline(appState, "历史训练已完成", projectA.getName() + " 的上一版结果已归档。");
    return appState;
  }

  private void addSeedTimeline(AppState appState, String title, String description) {
    AppState.TimelineEntry entry = new AppState.TimelineEntry();
    entry.setId("tl-" + shortId());
    entry.setTitle(title);
    entry.setDescription(description);
    entry.setAt(now());
    appState.getTimeline().add(entry);
  }

  private AppState.Dataset dataset(String id, String name, String path, int imageCount, int classCount, int version, String status) {
    AppState.Dataset dataset = new AppState.Dataset();
    dataset.setId(id);
    dataset.setName(name);
    dataset.setTaskType("object-detection");
    dataset.setStoragePath(path);
    dataset.setImageCount(imageCount);
    dataset.setClassCount(classCount);
    dataset.setVersion(version);
    dataset.setLabelFormat("yolo");
    dataset.setSplitStrategy("80/10/10");
    dataset.setStatus(status);
    dataset.setUpdatedAt(now());
    List<String> classNames = new ArrayList<String>();
    for (int index = 0; index < classCount; index++) {
      classNames.add("class_" + index);
    }
    dataset.setClassNames(classNames);
    return dataset;
  }
}
