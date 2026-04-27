package com.souleyez.assistant.service;

import com.souleyez.assistant.domain.AppState;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuickStartService {
  private static final Logger LOGGER = LoggerFactory.getLogger(QuickStartService.class);
  private final Path quickStartRoot = Paths.get("data", "runtime", "quick-start");
  private final Path chunkUploadRoot = quickStartRoot.resolve("_chunk-uploads");
  private final ExecutorService quickStartExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      thread.setName("quick-start-worker");
      return thread;
    }
  });
  private final AppStateStore store;
  private final GemmaRuntimeService gemmaRuntimeService;
  private final AutoLabelService autoLabelService;

  public QuickStartService(AppStateStore store,
                           GemmaRuntimeService gemmaRuntimeService,
                           AutoLabelService autoLabelService) {
    this.store = store;
    this.gemmaRuntimeService = gemmaRuntimeService;
    this.autoLabelService = autoLabelService;
  }

  @PreDestroy
  public void shutdown() {
    quickStartExecutor.shutdownNow();
  }

  public Map<String, Object> summary() {
    Map<String, Object> runtime = new LinkedHashMap<String, Object>();
    runtime.put("gemmaReady", gemmaRuntimeService.isAvailable());
    runtime.put("gemmaModel", gemmaRuntimeService.getConfiguredModel());
    runtime.put("accepts", new String[] {"zip", "jpg", "jpeg", "png", "webp", "bmp"});
    runtime.put("autoStartWhenLabeled", Boolean.TRUE);
    runtime.put("autoLabelEnabled", autoLabelService.isEnabled());
    runtime.put("autoLabelModel", autoLabelService.getConfiguredModel());
    runtime.put("autoLabelConfidence", autoLabelService.getConfThreshold());
    runtime.put("storageRoot", quickStartRoot.toString());

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("runtime", runtime);
    response.put("items", store.snapshot().getQuickStarts());
    return response;
  }

  public Map<String, Object> createQuickStart(MultipartFile[] files,
                                              String targetDescription,
                                              boolean autoStart) throws IOException {
    if (!StringUtils.hasText(targetDescription)) {
      throw new IllegalArgumentException("请先描述要捕获的目标。");
    }
    if (files == null || files.length == 0) {
      throw new IllegalArgumentException("请至少上传一张图片或一个 zip。");
    }
    Files.createDirectories(quickStartRoot);

    String quickStartId = "qs-" + UUID.randomUUID().toString().substring(0, 8);
    Path runRoot = quickStartRoot.resolve(quickStartId);
    Path incomingRoot = runRoot.resolve("incoming");
    Path extractedRoot = runRoot.resolve("source");
    Path datasetRoot = runRoot.resolve("dataset");
    Files.createDirectories(incomingRoot);

    int uploadedFileCount = saveUploads(files, incomingRoot);
    if (uploadedFileCount == 0) {
      throw new IllegalArgumentException("请至少上传一张图片或一个 zip。");
    }

    AppState.QuickStartSession session = buildUploadedSession(quickStartId, targetDescription, incomingRoot, uploadedFileCount);
    store.recordQuickStart(session);
    LOGGER.info("Quick Start {} accepted: {} uploaded files", quickStartId, uploadedFileCount);
    enqueueQuickStartProcessing(
        quickStartId,
        targetDescription,
        autoStart,
        runRoot,
        incomingRoot,
        extractedRoot,
        datasetRoot,
        session.getCreatedAt()
    );

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", session);
    response.put("dataset", null);
    response.put("project", null);
    response.put("job", null);
    response.put("runtime", summary().get("runtime"));
    return response;
  }

  public Map<String, Object> uploadChunk(String uploadId,
                                         String fileName,
                                         int fileIndex,
                                         int chunkIndex,
                                         MultipartFile chunk) throws IOException {
    if (!StringUtils.hasText(uploadId)) {
      throw new IllegalArgumentException("上传批次不能为空。");
    }
    if (!StringUtils.hasText(fileName)) {
      throw new IllegalArgumentException("文件名不能为空。");
    }
    if (fileIndex < 0 || chunkIndex < 0) {
      throw new IllegalArgumentException("分片编号不正确。");
    }
    if (chunk == null || chunk.isEmpty()) {
      throw new IllegalArgumentException("上传分片为空。");
    }

    Path fileChunkRoot = chunkFileRoot(uploadId, fileIndex, fileName);
    Files.createDirectories(fileChunkRoot);
    Files.write(fileChunkRoot.resolve("filename.txt"), sanitizeFileName(fileName).getBytes("UTF-8"));
    Path chunkPath = fileChunkRoot.resolve(String.format(Locale.ROOT, "chunk-%06d.part", chunkIndex));
    try (InputStream inputStream = chunk.getInputStream()) {
      Files.copy(inputStream, chunkPath, StandardCopyOption.REPLACE_EXISTING);
    }

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("uploadId", sanitizeUploadId(uploadId));
    response.put("fileIndex", fileIndex);
    response.put("chunkIndex", chunkIndex);
    response.put("received", Boolean.TRUE);
    return response;
  }

  public Map<String, Object> completeChunkedQuickStart(String uploadId,
                                                       String targetDescription,
                                                       boolean autoStart) throws IOException {
    if (!StringUtils.hasText(targetDescription)) {
      throw new IllegalArgumentException("请先描述要捕获的目标。");
    }
    Path uploadRoot = chunkUploadRoot.resolve(sanitizeUploadId(uploadId));
    if (!Files.isDirectory(uploadRoot)) {
      throw new IllegalArgumentException("没有找到上传分片，请重新上传。");
    }

    Files.createDirectories(quickStartRoot);
    String quickStartId = "qs-" + UUID.randomUUID().toString().substring(0, 8);
    Path runRoot = quickStartRoot.resolve(quickStartId);
    Path incomingRoot = runRoot.resolve("incoming");
    Path extractedRoot = runRoot.resolve("source");
    Path datasetRoot = runRoot.resolve("dataset");
    Files.createDirectories(incomingRoot);

    int uploadedFileCount = assembleChunkedUploads(uploadRoot, incomingRoot);
    if (uploadedFileCount == 0) {
      throw new IllegalArgumentException("没有找到可合并的上传文件，请重新上传。");
    }

    AppState.QuickStartSession session = buildUploadedSession(quickStartId, targetDescription, incomingRoot, uploadedFileCount);
    store.recordQuickStart(session);
    LOGGER.info("Quick Start {} accepted from chunked upload {}: {} files", quickStartId, sanitizeUploadId(uploadId), uploadedFileCount);
    enqueueQuickStartProcessing(
        quickStartId,
        targetDescription,
        autoStart,
        runRoot,
        incomingRoot,
        extractedRoot,
        datasetRoot,
        session.getCreatedAt()
    );
    deleteRecursively(uploadRoot);

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", session);
    response.put("dataset", null);
    response.put("project", null);
    response.put("job", null);
    response.put("runtime", summary().get("runtime"));
    return response;
  }

  private void enqueueQuickStartProcessing(final String quickStartId,
                                           final String targetDescription,
                                           final boolean autoStart,
                                           final Path runRoot,
                                           final Path incomingRoot,
                                           final Path extractedRoot,
                                           final Path datasetRoot,
                                           final String createdAt) {
    quickStartExecutor.submit(new Runnable() {
      @Override
      public void run() {
        processQuickStart(quickStartId, targetDescription, autoStart, runRoot, incomingRoot, extractedRoot, datasetRoot, createdAt);
      }
    });
  }

  private void processQuickStart(String quickStartId,
                                 String targetDescription,
                                 boolean autoStart,
                                 Path runRoot,
                                 Path incomingRoot,
                                 Path extractedRoot,
                                 Path datasetRoot,
                                 String createdAt) {
    DatasetScan initialScan = null;
    try {
      LOGGER.info("Quick Start {} processing started", quickStartId);
      updateQuickStartProgress(
          quickStartId,
          targetDescription,
          incomingRoot,
          0,
          0,
          createdAt,
          "extracting",
          "上传已完成，后台正在解压和整理图片。"
      );

    expandIncomingUploads(incomingRoot, extractedRoot);
    initialScan = inspectUploads(extractedRoot, datasetRoot);
    if (initialScan.getImageCount() == 0) {
      throw new IllegalArgumentException("未识别到图片文件，支持 zip 或 jpg/png/webp/bmp。");
    }

    updateQuickStartProgress(
        quickStartId,
        targetDescription,
        initialScan,
        createdAt,
        "gemma-planning",
        "Gemma 4 正在理解目标描述并生成训练方案。"
    );

    GemmaRuntimeService.QuickStartPlan plan = gemmaRuntimeService.planQuickStart(
        targetDescription,
        initialScan.getImageCount(),
        initialScan.getLabeledImageCount(),
        initialScan.getSampleNames()
    );

    AutoLabelService.AutoLabelReport autoLabelReport = null;
    DatasetScan effectiveScan = initialScan;
    if (!initialScan.isReadyForTraining()) {
      updateQuickStartProgress(
          quickStartId,
          targetDescription,
          initialScan,
          createdAt,
          "auto-labeling",
          "正在按 Gemma 解析出的目标自动预标注图片。"
      );
      autoLabelReport = autoLabelService.autoLabel(
          runRoot,
          initialScan.getDatasetPath(),
          plan.getClassNames(),
          plan.getDetectionPrompts(),
          plan.getImageSize()
      );
      effectiveScan = scanPreparedDataset(initialScan.getDatasetPath());
    }

    AppState.Dataset datasetRequest = new AppState.Dataset();
    datasetRequest.setName(plan.getDatasetName());
    datasetRequest.setTaskType("object-detection");
    datasetRequest.setStoragePath(effectiveScan.getDatasetPath().toString());
    datasetRequest.setImageCount(effectiveScan.getImageCount());
    datasetRequest.setClassCount(plan.getClassNames().size());
    datasetRequest.setVersion(1);
    datasetRequest.setLabelFormat("yolo");
    datasetRequest.setSplitStrategy("80/10/10");
    datasetRequest.setStatus(effectiveScan.isReadyForTraining() ? "ready" : "reviewing");
    datasetRequest.setNotes(buildDatasetNotes(targetDescription, initialScan, effectiveScan, plan, autoLabelReport));
    datasetRequest.setClassNames(plan.getClassNames());
    AppState.Dataset dataset = store.createDataset(datasetRequest);

    AppState.TrainingProject projectRequest = new AppState.TrainingProject();
    projectRequest.setName(plan.getProjectName());
    projectRequest.setObjective(plan.getObjective());
    projectRequest.setDatasetId(dataset.getId());
    projectRequest.setYoloVersion(plan.getYoloVersion());
    projectRequest.setImageSize(plan.getImageSize());
    projectRequest.setEpochs(plan.getEpochs());
    projectRequest.setBatchSize(plan.getBatchSize());
    projectRequest.setOptimizer(plan.getOptimizer());
    projectRequest.setStatus(effectiveScan.isReadyForTraining() ? "draft" : "reviewing");
    projectRequest.setOwner("quick-start");
    AppState.TrainingProject project = store.createProject(projectRequest);

    AppState.TrainingJob job = null;
    String warning = buildWarning(initialScan, effectiveScan, autoLabelReport);
    boolean autoStarted = false;
    if (effectiveScan.isReadyForTraining() && autoStart) {
      try {
        job = store.startJob(project.getId());
        autoStarted = true;
      } catch (IOException launchError) {
        warning = "项目已创建，但训练任务未能自动启动: " + launchError.getMessage();
      }
    }

    AppState.QuickStartSession session = new AppState.QuickStartSession();
    session.setId(quickStartId);
    session.setTargetDescription(targetDescription);
    session.setDatasetId(dataset.getId());
    session.setDatasetName(dataset.getName());
    session.setProjectId(project.getId());
    session.setProjectName(project.getName());
    session.setJobId(job == null ? null : job.getId());
    session.setUploadPath(effectiveScan.getDatasetPath().toString());
    session.setImageCount(effectiveScan.getImageCount());
    session.setLabeledImageCount(effectiveScan.getLabeledImageCount());
    session.setReadyForTraining(effectiveScan.isReadyForTraining());
    session.setAutoStarted(autoStarted);
    session.setStatus(resolveStatus(effectiveScan.isReadyForTraining(), autoStarted, job, autoLabelReport, warning));
    session.setObjective(project.getObjective());
    session.setWarning(warning);
    session.setNextAction(buildNextAction(effectiveScan.isReadyForTraining(), autoStarted, autoLabelReport));
    session.setGemmaSummary(plan.getSummary());
    session.setSuggestedClasses(new ArrayList<String>(plan.getClassNames()));
    session.setAutoLabelStatus(autoLabelReport == null ? null : autoLabelReport.getStatus());
    session.setAutoLabelModel(autoLabelReport == null ? null : autoLabelReport.getModel());
    session.setAutoLabelCreatedCount(autoLabelReport == null ? 0 : autoLabelReport.getCreatedLabelCount());
    session.setAutoLabelRemainingCount(autoLabelReport == null ? 0 : autoLabelReport.getRemainingImageCount());
    session.setAutoLabelCoverage(autoLabelReport == null ? 0.0d : roundCoverage(autoLabelReport.coverage()));
    session.setAutoLabelMessage(autoLabelReport == null ? null : autoLabelReport.getMessage());
    session.setCreatedAt(createdAt);
    store.updateQuickStart(session);
    LOGGER.info("Quick Start {} completed with status {}", quickStartId, session.getStatus());
    } catch (Exception exception) {
      LOGGER.warn("Quick Start {} failed", quickStartId, exception);
      try {
        AppState.QuickStartSession failed = buildFailedSession(
            quickStartId,
            targetDescription,
            initialScan,
            incomingRoot,
            createdAt,
            exception
        );
        store.updateQuickStart(failed);
      } catch (IOException ignored) {
        // best-effort async status update
      }
    }
  }

  private AppState.QuickStartSession buildProcessingSession(String quickStartId,
                                                            String targetDescription,
                                                            DatasetScan initialScan) {
    return buildProcessingSession(
        quickStartId,
        targetDescription,
        initialScan.getDatasetPath(),
        initialScan.getImageCount(),
        initialScan.getLabeledImageCount()
    );
  }

  private AppState.QuickStartSession buildUploadedSession(String quickStartId,
                                                          String targetDescription,
                                                          Path incomingRoot,
                                                          int uploadedFileCount) {
    AppState.QuickStartSession session = buildProcessingSession(quickStartId, targetDescription, incomingRoot, 0, 0);
    session.setStatus("uploaded");
    session.setNextAction("上传已完成，后台会慢慢解压、识别目标并自动预标注。");
    session.setGemmaSummary("已接收 " + uploadedFileCount + " 个文件。图片扫描和 Gemma 解析将在后台执行。");
    return session;
  }

  private AppState.QuickStartSession buildProcessingSession(String quickStartId,
                                                            String targetDescription,
                                                            Path uploadPath,
                                                            int imageCount,
                                                            int labeledImageCount) {
    AppState.QuickStartSession session = new AppState.QuickStartSession();
    session.setId(quickStartId);
    session.setTargetDescription(targetDescription);
    session.setDatasetName("待生成数据集");
    session.setProjectName("训练方案生成中");
    session.setUploadPath(uploadPath.toString());
    session.setImageCount(imageCount);
    session.setLabeledImageCount(labeledImageCount);
    session.setReadyForTraining(false);
    session.setAutoStarted(false);
    session.setStatus("processing");
    session.setObjective(buildObjectivePreview(targetDescription));
    session.setWarning(null);
    session.setNextAction("上传已完成，系统正在后台解析目标、预标注并准备训练。");
    session.setGemmaSummary("后台处理中。大批图片会继续排队处理，页面可稍后刷新查看结果。");
    session.setSuggestedClasses(new ArrayList<String>());
    session.setCreatedAt(Instant.now().toString());
    return session;
  }

  private void updateQuickStartProgress(String quickStartId,
                                        String targetDescription,
                                        DatasetScan scan,
                                        String createdAt,
                                        String status,
                                        String nextAction) throws IOException {
    updateQuickStartProgress(
        quickStartId,
        targetDescription,
        scan.getDatasetPath(),
        scan.getImageCount(),
        scan.getLabeledImageCount(),
        createdAt,
        status,
        nextAction
    );
  }

  private void updateQuickStartProgress(String quickStartId,
                                        String targetDescription,
                                        Path uploadPath,
                                        int imageCount,
                                        int labeledImageCount,
                                        String createdAt,
                                        String status,
                                        String nextAction) throws IOException {
    AppState.QuickStartSession session = buildProcessingSession(
        quickStartId,
        targetDescription,
        uploadPath,
        imageCount,
        labeledImageCount
    );
    session.setCreatedAt(createdAt);
    session.setStatus(status);
    session.setNextAction(nextAction);
    store.updateQuickStart(session);
  }

  private AppState.QuickStartSession buildFailedSession(String quickStartId,
                                                        String targetDescription,
                                                        DatasetScan scan,
                                                        Path fallbackPath,
                                                        String createdAt,
                                                        Exception exception) {
    AppState.QuickStartSession session = scan == null
        ? buildProcessingSession(quickStartId, targetDescription, fallbackPath, 0, 0)
        : buildProcessingSession(quickStartId, targetDescription, scan);
    session.setCreatedAt(createdAt);
    session.setStatus("failed");
    session.setWarning("后台处理失败: " + defaultText(exception.getMessage(), exception.getClass().getSimpleName()));
    session.setNextAction("上传文件已保存。请检查文件是否为图片或 YOLO 数据集 zip；如果仍失败，再查看后端日志。");
    session.setGemmaSummary("上传已保存，但后台解压、Gemma 解析、自动预标注或项目创建阶段失败。");
    return session;
  }

  private String buildObjectivePreview(String targetDescription) {
    if (StringUtils.hasText(targetDescription)) {
      return "围绕“" + targetDescription.trim() + "”生成检测训练方案。";
    }
    return "生成检测训练方案。";
  }

  private Path chunkFileRoot(String uploadId, int fileIndex, String fileName) {
    String safeFileName = sanitizeFileName(fileName);
    String safeDirName = String.format(Locale.ROOT, "%05d-%s", fileIndex, safeFileName).replaceAll("[^a-zA-Z0-9._-]+", "_");
    return chunkUploadRoot.resolve(sanitizeUploadId(uploadId)).resolve(safeDirName);
  }

  private int assembleChunkedUploads(Path uploadRoot, Path incomingRoot) throws IOException {
    List<Path> fileRoots = new ArrayList<Path>();
    try (Stream<Path> stream = Files.list(uploadRoot)) {
      stream.filter(Files::isDirectory).forEach(fileRoots::add);
    }
    Collections.sort(fileRoots);

    int assembledCount = 0;
    for (Path fileRoot : fileRoots) {
      String fileName = readChunkFileName(fileRoot);
      List<Path> chunks = new ArrayList<Path>();
      try (Stream<Path> stream = Files.list(fileRoot)) {
        stream.filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(".part"))
            .forEach(chunks::add);
      }
      Collections.sort(chunks);
      if (chunks.isEmpty()) {
        continue;
      }
      Path target = resolveUniqueIncomingFile(incomingRoot, fileName);
      try (OutputStream outputStream = Files.newOutputStream(target)) {
        for (Path chunkPath : chunks) {
          Files.copy(chunkPath, outputStream);
        }
      }
      assembledCount++;
    }
    return assembledCount;
  }

  private String readChunkFileName(Path fileRoot) throws IOException {
    Path fileNamePath = fileRoot.resolve("filename.txt");
    if (Files.exists(fileNamePath)) {
      return sanitizeFileName(new String(Files.readAllBytes(fileNamePath), "UTF-8"));
    }
    String fallback = fileRoot.getFileName().toString();
    int separator = fallback.indexOf('-');
    return sanitizeFileName(separator >= 0 ? fallback.substring(separator + 1) : fallback);
  }

  private Path resolveUniqueIncomingFile(Path incomingRoot, String fileName) throws IOException {
    String safeName = sanitizeFileName(fileName);
    Path candidate = incomingRoot.resolve(safeName);
    if (!Files.exists(candidate)) {
      return candidate;
    }
    String baseName = stripExtension(safeName);
    String extension = extensionOf(safeName);
    for (int index = 1; index < 10000; index++) {
      candidate = incomingRoot.resolve(baseName + "-" + index + extension);
      if (!Files.exists(candidate)) {
        return candidate;
      }
    }
    throw new IOException("无法生成唯一文件名: " + safeName);
  }

  private void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    List<Path> paths = new ArrayList<Path>();
    try (Stream<Path> stream = Files.walk(root)) {
      stream.forEach(paths::add);
    }
    Collections.sort(paths, new Comparator<Path>() {
      @Override
      public int compare(Path left, Path right) {
        return Integer.compare(right.getNameCount(), left.getNameCount());
      }
    });
    for (Path path : paths) {
      Files.deleteIfExists(path);
    }
  }

  private int saveUploads(MultipartFile[] files, Path incomingRoot) throws IOException {
    int uploadedFileCount = 0;
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        continue;
      }
      String safeName = sanitizeFileName(file.getOriginalFilename());
      Path incomingFile = incomingRoot.resolve(safeName);
      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, incomingFile, StandardCopyOption.REPLACE_EXISTING);
      }
      uploadedFileCount++;
    }
    return uploadedFileCount;
  }

  private void expandIncomingUploads(Path incomingRoot, Path extractedRoot) throws IOException {
    Files.createDirectories(extractedRoot);
    try (Stream<Path> stream = Files.list(incomingRoot)) {
      List<Path> uploads = new ArrayList<Path>();
      stream.filter(Files::isRegularFile).forEach(uploads::add);
      Collections.sort(uploads);
      for (Path incomingFile : uploads) {
        String safeName = incomingFile.getFileName().toString();
        if (safeName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
          unzip(incomingFile, extractedRoot.resolve(stripExtension(safeName)));
        } else {
          Path flatRoot = extractedRoot.resolve("flat");
          Files.createDirectories(flatRoot);
          Files.copy(incomingFile, flatRoot.resolve(safeName), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private DatasetScan inspectUploads(Path extractedRoot, Path datasetRoot) throws IOException {
    Path structuredRoot = findStructuredDatasetRoot(extractedRoot);
    if (structuredRoot != null) {
      List<Path> structuredImages = collectImages(structuredRoot);
      int labeledImageCount = countStructuredLabels(structuredRoot, structuredImages);
      return new DatasetScan(structuredRoot, structuredImages.size(), labeledImageCount, sampleNames(structuredImages));
    }
    return prepareFlatDataset(extractedRoot, datasetRoot);
  }

  private Path findStructuredDatasetRoot(Path root) throws IOException {
    List<Path> directories = new ArrayList<Path>();
    try (Stream<Path> stream = Files.walk(root)) {
      stream.filter(Files::isDirectory).forEach(directories::add);
    }
    Collections.sort(directories, new Comparator<Path>() {
      @Override
      public int compare(Path left, Path right) {
        return Integer.compare(left.getNameCount(), right.getNameCount());
      }
    });
    for (Path candidate : directories) {
      if (Files.isDirectory(candidate.resolve(Paths.get("train", "images")))
          && Files.isDirectory(candidate.resolve(Paths.get("val", "images")))) {
        return candidate;
      }
    }
    return null;
  }

  private DatasetScan prepareFlatDataset(Path extractedRoot, Path datasetRoot) throws IOException {
    List<Path> images = collectImages(extractedRoot);
    Collections.sort(images);
    if (images.isEmpty()) {
      return new DatasetScan(datasetRoot, 0, 0, Collections.<String>emptyList());
    }
    Files.createDirectories(datasetRoot);
    int trainCutoff = Math.max(1, (int) Math.floor(images.size() * 0.8d));
    int valCutoff = Math.max(trainCutoff + 1, (int) Math.floor(images.size() * 0.9d));
    int labeledCount = 0;
    for (int index = 0; index < images.size(); index++) {
      Path image = images.get(index);
      String split = index < trainCutoff ? "train" : (index < valCutoff ? "val" : "test");
      Path imageTargetDir = datasetRoot.resolve(Paths.get(split, "images"));
      Path labelTargetDir = datasetRoot.resolve(Paths.get(split, "labels"));
      Files.createDirectories(imageTargetDir);
      Files.createDirectories(labelTargetDir);
      String baseName = String.format(Locale.ROOT, "img-%05d", index + 1);
      String extension = extensionOf(image.getFileName().toString());
      Files.copy(image, imageTargetDir.resolve(baseName + extension), StandardCopyOption.REPLACE_EXISTING);
      Path label = siblingLabelOf(image);
      if (label != null && Files.exists(label)) {
        Files.copy(label, labelTargetDir.resolve(baseName + ".txt"), StandardCopyOption.REPLACE_EXISTING);
        labeledCount++;
      }
    }
    return new DatasetScan(datasetRoot, images.size(), labeledCount, sampleNames(images));
  }

  private List<Path> collectImages(Path root) throws IOException {
    List<Path> images = new ArrayList<Path>();
    try (Stream<Path> stream = Files.walk(root)) {
      stream.filter(Files::isRegularFile)
          .filter(path -> isImageFile(path.getFileName().toString()))
          .forEach(images::add);
    }
    return images;
  }

  private int countStructuredLabels(Path structuredRoot, List<Path> images) {
    int labeled = 0;
    for (Path image : images) {
      Path relative = structuredRoot.relativize(image);
      if (relative.getNameCount() < 2) {
        continue;
      }
      Path split = relative.subpath(0, 1);
      Path fileName = relative.getFileName();
      String labelName = stripExtension(fileName.toString()) + ".txt";
      Path labelPath = structuredRoot.resolve(split).resolve("labels").resolve(labelName);
      if (Files.exists(labelPath)) {
        labeled++;
      }
    }
    return labeled;
  }

  private DatasetScan scanPreparedDataset(Path datasetRoot) throws IOException {
    List<Path> images = collectImages(datasetRoot);
    int labeledImageCount = countStructuredLabels(datasetRoot, images);
    return new DatasetScan(datasetRoot, images.size(), labeledImageCount, sampleNames(images));
  }

  private List<String> sampleNames(List<Path> images) {
    List<String> samples = new ArrayList<String>();
    for (Path image : images) {
      samples.add(image.getFileName().toString());
      if (samples.size() >= 8) {
        break;
      }
    }
    return samples;
  }

  private void unzip(Path zipFile, Path destinationRoot) throws IOException {
    Files.createDirectories(destinationRoot);
    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        Path target = destinationRoot.resolve(entry.getName()).normalize();
        if (!target.startsWith(destinationRoot)) {
          throw new IllegalArgumentException("Zip 内包含非法路径: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(zipInputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private Path siblingLabelOf(Path image) {
    Path parent = image.getParent();
    if (parent == null) {
      return null;
    }
    return parent.resolve(stripExtension(image.getFileName().toString()) + ".txt");
  }

  private boolean isImageFile(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".png")
        || lower.endsWith(".webp")
        || lower.endsWith(".bmp");
  }

  private String sanitizeFileName(String originalName) {
    String fileName = StringUtils.hasText(originalName) ? originalName : "upload.bin";
    return fileName.replace("\\", "/").replaceAll(".*/", "");
  }

  private String sanitizeUploadId(String uploadId) {
    String normalized = StringUtils.hasText(uploadId) ? uploadId : "upload";
    normalized = normalized.replaceAll("[^a-zA-Z0-9._-]+", "-");
    if (!StringUtils.hasText(normalized)) {
      return "upload";
    }
    return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
  }

  private String stripExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index > 0 ? fileName.substring(0, index) : fileName;
  }

  private String extensionOf(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index >= 0 ? fileName.substring(index) : "";
  }

  private String buildDatasetNotes(String targetDescription,
                                   DatasetScan initialScan,
                                   DatasetScan effectiveScan,
                                   GemmaRuntimeService.QuickStartPlan plan,
                                   AutoLabelService.AutoLabelReport autoLabelReport) {
    StringBuilder notes = new StringBuilder();
    notes.append("Quick Start 目标: ").append(targetDescription).append('\n');
    notes.append("Gemma 解析类别: ").append(plan.getClassNames()).append('\n');
    notes.append("Gemma 检测提示: ").append(plan.getDetectionPrompts()).append('\n');
    notes.append("初始图片/标注: ")
        .append(initialScan.getImageCount())
        .append('/')
        .append(initialScan.getLabeledImageCount())
        .append('\n');
    notes.append("当前图片/标注: ")
        .append(effectiveScan.getImageCount())
        .append('/')
        .append(effectiveScan.getLabeledImageCount())
        .append('\n');
    if (autoLabelReport != null) {
      notes.append("自动预标注: ")
          .append(defaultText(autoLabelReport.getStatus(), "unknown"))
          .append(" · model=")
          .append(defaultText(autoLabelReport.getModel(), "n/a"))
          .append(" · created=")
          .append(autoLabelReport.getCreatedLabelCount())
          .append(" · remaining=")
          .append(autoLabelReport.getRemainingImageCount())
          .append('\n');
      notes.append("自动预标注说明: ").append(defaultText(autoLabelReport.getMessage(), "")).append('\n');
    }
    notes.append(plan.getSummary());
    return notes.toString();
  }

  private String resolveStatus(boolean readyForTraining,
                               boolean autoStarted,
                               AppState.TrainingJob job,
                               AutoLabelService.AutoLabelReport autoLabelReport,
                               String warning) {
    if (autoStarted && job != null) {
      return job.getStatus();
    }
    if (readyForTraining) {
      return StringUtils.hasText(warning) ? "prepared" : "ready";
    }
    if (autoLabelReport != null && StringUtils.hasText(autoLabelReport.getStatus())) {
      return "auto-label-" + autoLabelReport.getStatus();
    }
    return "awaiting-labels";
  }

  private String buildNextAction(boolean readyForTraining,
                                 boolean autoStarted,
                                 AutoLabelService.AutoLabelReport autoLabelReport) {
    if (autoStarted) {
      return "训练任务已自动创建，直接去训练任务页查看日志和进度。";
    }
    if (readyForTraining) {
      if (autoLabelReport != null && autoLabelReport.getCreatedLabelCount() > 0) {
        return "系统已经自动补齐缺失标签，建议先抽检一轮标注，再直接开训。";
      }
      return "数据集和项目已创建完成，去训练任务页点一次开始训练即可。";
    }
    if (autoLabelReport != null) {
      if ("partial".equals(autoLabelReport.getStatus())) {
        return "系统已自动预标注一部分图片，先复核剩余未覆盖样本，再从训练任务页开训。";
      }
      if ("empty".equals(autoLabelReport.getStatus())) {
        return "自动预标注没有产出可靠框，建议先人工补一批 YOLO 标签，再继续训练。";
      }
      if ("failed".equals(autoLabelReport.getStatus())) {
        return "自动预标注执行失败，建议检查 YOLOWorld 权重和 Python 环境，然后补齐标签后再开训。";
      }
    }
    return "系统已经建好数据集目录和训练项目，先补齐 YOLO 标签文件，再从训练任务页开训。";
  }

  private String buildWarning(DatasetScan initialScan,
                              DatasetScan effectiveScan,
                              AutoLabelService.AutoLabelReport autoLabelReport) {
    if (effectiveScan.isReadyForTraining()) {
      if (autoLabelReport != null && autoLabelReport.getCreatedLabelCount() > 0) {
        return "系统已自动补齐缺失 YOLO 标签，建议先抽检再进入训练。";
      }
      return null;
    }
    if (autoLabelReport == null) {
      return "当前只检测到图片或部分标注，已先创建数据集和项目，补齐 YOLO 标签后再开训。";
    }
    if ("partial".equals(autoLabelReport.getStatus())) {
      return "已自动预标注 "
          + autoLabelReport.getCreatedLabelCount()
          + " 张，当前总标注覆盖 "
          + effectiveScan.getLabeledImageCount()
          + "/"
          + effectiveScan.getImageCount()
          + "，剩余样本需要复核或手工补框。";
    }
    if ("empty".equals(autoLabelReport.getStatus())) {
      return "系统已尝试自动预标注，但没有生成可靠检测框，当前仍需人工补齐 YOLO 标签。";
    }
    if ("failed".equals(autoLabelReport.getStatus())) {
      return "自动预标注失败: " + defaultText(autoLabelReport.getMessage(), "unknown error");
    }
    if ("missing-prompts".equals(autoLabelReport.getStatus())) {
      return "当前没有可用于自动预标注的检测提示词，系统已先创建数据集和项目。";
    }
    if (initialScan.getLabeledImageCount() < initialScan.getImageCount()) {
      return "当前只检测到图片或部分标注，已先创建数据集和项目，补齐 YOLO 标签后再开训。";
    }
    return null;
  }

  private String defaultText(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private double roundCoverage(double value) {
    return Math.round(value * 1000.0d) / 1000.0d;
  }

  private static class DatasetScan {
    private final Path datasetPath;
    private final int imageCount;
    private final int labeledImageCount;
    private final List<String> sampleNames;

    private DatasetScan(Path datasetPath, int imageCount, int labeledImageCount, List<String> sampleNames) {
      this.datasetPath = datasetPath;
      this.imageCount = imageCount;
      this.labeledImageCount = labeledImageCount;
      this.sampleNames = sampleNames;
    }

    public Path getDatasetPath() {
      return datasetPath;
    }

    public int getImageCount() {
      return imageCount;
    }

    public int getLabeledImageCount() {
      return labeledImageCount;
    }

    public boolean isReadyForTraining() {
      return imageCount > 0 && labeledImageCount == imageCount;
    }

    public List<String> getSampleNames() {
      return sampleNames;
    }
  }
}
