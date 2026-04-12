package com.souleyez.assistant.service;

import com.souleyez.assistant.domain.AppState;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuickStartService {
  private final Path quickStartRoot = Paths.get("data", "runtime", "quick-start");
  private final AppStateStore store;
  private final GemmaRuntimeService gemmaRuntimeService;

  public QuickStartService(AppStateStore store, GemmaRuntimeService gemmaRuntimeService) {
    this.store = store;
    this.gemmaRuntimeService = gemmaRuntimeService;
  }

  public Map<String, Object> summary() {
    Map<String, Object> runtime = new LinkedHashMap<String, Object>();
    runtime.put("gemmaReady", gemmaRuntimeService.isAvailable());
    runtime.put("gemmaModel", gemmaRuntimeService.getConfiguredModel());
    runtime.put("accepts", new String[] {"zip", "jpg", "jpeg", "png", "webp", "bmp"});
    runtime.put("autoStartWhenLabeled", Boolean.TRUE);
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
    Files.createDirectories(extractedRoot);

    materializeUploads(files, incomingRoot, extractedRoot);

    DatasetScan scan = inspectUploads(extractedRoot, datasetRoot);
    if (scan.getImageCount() == 0) {
      throw new IllegalArgumentException("未识别到图片文件，支持 zip 或 jpg/png/webp/bmp。");
    }

    GemmaRuntimeService.QuickStartPlan plan = gemmaRuntimeService.planQuickStart(
        targetDescription,
        scan.getImageCount(),
        scan.getLabeledImageCount(),
        scan.getSampleNames()
    );

    AppState.Dataset datasetRequest = new AppState.Dataset();
    datasetRequest.setName(plan.getDatasetName());
    datasetRequest.setTaskType("object-detection");
    datasetRequest.setStoragePath(scan.getDatasetPath().toString());
    datasetRequest.setImageCount(scan.getImageCount());
    datasetRequest.setClassCount(plan.getClassNames().size());
    datasetRequest.setVersion(1);
    datasetRequest.setLabelFormat("yolo");
    datasetRequest.setSplitStrategy("80/10/10");
    datasetRequest.setStatus(scan.isReadyForTraining() ? "ready" : "reviewing");
    datasetRequest.setNotes(buildDatasetNotes(targetDescription, scan, plan));
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
    projectRequest.setStatus(scan.isReadyForTraining() ? "draft" : "reviewing");
    projectRequest.setOwner("quick-start");
    AppState.TrainingProject project = store.createProject(projectRequest);

    AppState.TrainingJob job = null;
    String warning = null;
    boolean autoStarted = false;
    if (scan.isReadyForTraining() && autoStart) {
      try {
        job = store.startJob(project.getId());
        autoStarted = true;
      } catch (IOException launchError) {
        warning = "项目已创建，但训练任务未能自动启动: " + launchError.getMessage();
      }
    } else if (!scan.isReadyForTraining()) {
      warning = "当前只检测到图片或部分标注，已先创建数据集和项目，补齐 YOLO 标签后再开训。";
    }

    AppState.QuickStartSession session = new AppState.QuickStartSession();
    session.setId(quickStartId);
    session.setTargetDescription(targetDescription);
    session.setDatasetId(dataset.getId());
    session.setDatasetName(dataset.getName());
    session.setProjectId(project.getId());
    session.setProjectName(project.getName());
    session.setJobId(job == null ? null : job.getId());
    session.setUploadPath(scan.getDatasetPath().toString());
    session.setImageCount(scan.getImageCount());
    session.setLabeledImageCount(scan.getLabeledImageCount());
    session.setReadyForTraining(scan.isReadyForTraining());
    session.setAutoStarted(autoStarted);
    session.setStatus(resolveStatus(scan.isReadyForTraining(), autoStarted, job, warning));
    session.setObjective(project.getObjective());
    session.setWarning(warning);
    session.setNextAction(buildNextAction(scan.isReadyForTraining(), autoStarted));
    session.setGemmaSummary(plan.getSummary());
    session.setSuggestedClasses(new ArrayList<String>(plan.getClassNames()));
    session.setCreatedAt(Instant.now().toString());
    store.recordQuickStart(session);

    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("item", session);
    response.put("dataset", dataset);
    response.put("project", project);
    response.put("job", job);
    response.put("runtime", summary().get("runtime"));
    return response;
  }

  private void materializeUploads(MultipartFile[] files, Path incomingRoot, Path extractedRoot) throws IOException {
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        continue;
      }
      String safeName = sanitizeFileName(file.getOriginalFilename());
      Path incomingFile = incomingRoot.resolve(safeName);
      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, incomingFile, StandardCopyOption.REPLACE_EXISTING);
      }
      if (safeName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
        unzip(incomingFile, extractedRoot.resolve(stripExtension(safeName)));
      } else {
        Path flatRoot = extractedRoot.resolve("flat");
        Files.createDirectories(flatRoot);
        Files.copy(incomingFile, flatRoot.resolve(safeName), StandardCopyOption.REPLACE_EXISTING);
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

  private String stripExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index > 0 ? fileName.substring(0, index) : fileName;
  }

  private String extensionOf(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index >= 0 ? fileName.substring(index) : "";
  }

  private String buildDatasetNotes(String targetDescription,
                                   DatasetScan scan,
                                   GemmaRuntimeService.QuickStartPlan plan) {
    StringBuilder notes = new StringBuilder();
    notes.append("Quick Start 目标: ").append(targetDescription).append('\n');
    notes.append("Gemma 解析类别: ").append(plan.getClassNames()).append('\n');
    notes.append("图片/标注: ").append(scan.getImageCount()).append('/').append(scan.getLabeledImageCount()).append('\n');
    notes.append(plan.getSummary());
    return notes.toString();
  }

  private String resolveStatus(boolean readyForTraining,
                               boolean autoStarted,
                               AppState.TrainingJob job,
                               String warning) {
    if (autoStarted && job != null) {
      return job.getStatus();
    }
    if (readyForTraining) {
      return StringUtils.hasText(warning) ? "prepared" : "ready";
    }
    return "awaiting-labels";
  }

  private String buildNextAction(boolean readyForTraining, boolean autoStarted) {
    if (autoStarted) {
      return "训练任务已自动创建，直接去训练任务页查看日志和进度。";
    }
    if (readyForTraining) {
      return "数据集和项目已创建完成，去训练任务页点一次开始训练即可。";
    }
    return "系统已经建好数据集目录和训练项目，先补齐 YOLO 标签文件，再从训练任务页开训。";
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
