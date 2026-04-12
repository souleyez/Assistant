package com.souleyez.assistant.domain;

import java.util.ArrayList;
import java.util.List;

public class AppState {
  private PlatformState platform;
  private List<Dataset> datasets = new ArrayList<Dataset>();
  private List<TrainingProject> projects = new ArrayList<TrainingProject>();
  private List<TrainingJob> jobs = new ArrayList<TrainingJob>();
  private List<ModelArtifact> models = new ArrayList<ModelArtifact>();
  private List<GemmaConversation> gemmaConversations = new ArrayList<GemmaConversation>();
  private List<QuickStartSession> quickStarts = new ArrayList<QuickStartSession>();
  private List<TimelineEntry> timeline = new ArrayList<TimelineEntry>();

  public PlatformState getPlatform() {
    return platform;
  }

  public void setPlatform(PlatformState platform) {
    this.platform = platform;
  }

  public List<Dataset> getDatasets() {
    return datasets;
  }

  public void setDatasets(List<Dataset> datasets) {
    this.datasets = datasets;
  }

  public List<TrainingProject> getProjects() {
    return projects;
  }

  public void setProjects(List<TrainingProject> projects) {
    this.projects = projects;
  }

  public List<TrainingJob> getJobs() {
    return jobs;
  }

  public void setJobs(List<TrainingJob> jobs) {
    this.jobs = jobs;
  }

  public List<ModelArtifact> getModels() {
    return models;
  }

  public void setModels(List<ModelArtifact> models) {
    this.models = models;
  }

  public List<GemmaConversation> getGemmaConversations() {
    return gemmaConversations;
  }

  public void setGemmaConversations(List<GemmaConversation> gemmaConversations) {
    this.gemmaConversations = gemmaConversations;
  }

  public List<QuickStartSession> getQuickStarts() {
    return quickStarts;
  }

  public void setQuickStarts(List<QuickStartSession> quickStarts) {
    this.quickStarts = quickStarts;
  }

  public List<TimelineEntry> getTimeline() {
    return timeline;
  }

  public void setTimeline(List<TimelineEntry> timeline) {
    this.timeline = timeline;
  }

  public static class PlatformState {
    private MachineProfile machine;
    private RuntimeProfile runtime;

    public MachineProfile getMachine() {
      return machine;
    }

    public void setMachine(MachineProfile machine) {
      this.machine = machine;
    }

    public RuntimeProfile getRuntime() {
      return runtime;
    }

    public void setRuntime(RuntimeProfile runtime) {
      this.runtime = runtime;
    }
  }

  public static class MachineProfile {
    private String hostName;
    private String mode;
    private String workspacePath;
    private String gpu;
    private int gpuCount;
    private int memoryGb;
    private String storagePolicy;

    public String getHostName() {
      return hostName;
    }

    public void setHostName(String hostName) {
      this.hostName = hostName;
    }

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode;
    }

    public String getWorkspacePath() {
      return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
      this.workspacePath = workspacePath;
    }

    public String getGpu() {
      return gpu;
    }

    public void setGpu(String gpu) {
      this.gpu = gpu;
    }

    public int getGpuCount() {
      return gpuCount;
    }

    public void setGpuCount(int gpuCount) {
      this.gpuCount = gpuCount;
    }

    public int getMemoryGb() {
      return memoryGb;
    }

    public void setMemoryGb(int memoryGb) {
      this.memoryGb = memoryGb;
    }

    public String getStoragePolicy() {
      return storagePolicy;
    }

    public void setStoragePolicy(String storagePolicy) {
      this.storagePolicy = storagePolicy;
    }
  }

  public static class RuntimeProfile {
    private String gemmaModel;
    private String yoloEngine;
    private String pythonEnv;
    private String pythonCommand;
    private String trainingWorkspace;
    private String defaultBaseModel;
    private boolean gemmaReady;
    private boolean yoloReady;
    private boolean trainingBusy;

    public String getGemmaModel() {
      return gemmaModel;
    }

    public void setGemmaModel(String gemmaModel) {
      this.gemmaModel = gemmaModel;
    }

    public String getYoloEngine() {
      return yoloEngine;
    }

    public void setYoloEngine(String yoloEngine) {
      this.yoloEngine = yoloEngine;
    }

    public String getPythonEnv() {
      return pythonEnv;
    }

    public void setPythonEnv(String pythonEnv) {
      this.pythonEnv = pythonEnv;
    }

    public String getPythonCommand() {
      return pythonCommand;
    }

    public void setPythonCommand(String pythonCommand) {
      this.pythonCommand = pythonCommand;
    }

    public String getTrainingWorkspace() {
      return trainingWorkspace;
    }

    public void setTrainingWorkspace(String trainingWorkspace) {
      this.trainingWorkspace = trainingWorkspace;
    }

    public String getDefaultBaseModel() {
      return defaultBaseModel;
    }

    public void setDefaultBaseModel(String defaultBaseModel) {
      this.defaultBaseModel = defaultBaseModel;
    }

    public boolean isGemmaReady() {
      return gemmaReady;
    }

    public void setGemmaReady(boolean gemmaReady) {
      this.gemmaReady = gemmaReady;
    }

    public boolean isYoloReady() {
      return yoloReady;
    }

    public void setYoloReady(boolean yoloReady) {
      this.yoloReady = yoloReady;
    }

    public boolean isTrainingBusy() {
      return trainingBusy;
    }

    public void setTrainingBusy(boolean trainingBusy) {
      this.trainingBusy = trainingBusy;
    }
  }

  public static class Dataset {
    private String id;
    private String name;
    private String taskType;
    private String storagePath;
    private int imageCount;
    private int classCount;
    private int version;
    private String labelFormat;
    private String splitStrategy;
    private String status;
    private String updatedAt;
    private String notes;
    private List<String> classNames = new ArrayList<String>();

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTaskType() {
      return taskType;
    }

    public void setTaskType(String taskType) {
      this.taskType = taskType;
    }

    public String getStoragePath() {
      return storagePath;
    }

    public void setStoragePath(String storagePath) {
      this.storagePath = storagePath;
    }

    public int getImageCount() {
      return imageCount;
    }

    public void setImageCount(int imageCount) {
      this.imageCount = imageCount;
    }

    public int getClassCount() {
      return classCount;
    }

    public void setClassCount(int classCount) {
      this.classCount = classCount;
    }

    public int getVersion() {
      return version;
    }

    public void setVersion(int version) {
      this.version = version;
    }

    public String getLabelFormat() {
      return labelFormat;
    }

    public void setLabelFormat(String labelFormat) {
      this.labelFormat = labelFormat;
    }

    public String getSplitStrategy() {
      return splitStrategy;
    }

    public void setSplitStrategy(String splitStrategy) {
      this.splitStrategy = splitStrategy;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
      this.updatedAt = updatedAt;
    }

    public String getNotes() {
      return notes;
    }

    public void setNotes(String notes) {
      this.notes = notes;
    }

    public List<String> getClassNames() {
      return classNames;
    }

    public void setClassNames(List<String> classNames) {
      this.classNames = classNames;
    }
  }

  public static class TrainingProject {
    private String id;
    private String name;
    private String objective;
    private String datasetId;
    private String datasetName;
    private String yoloVersion;
    private int imageSize;
    private int epochs;
    private int batchSize;
    private String optimizer;
    private String status;
    private String owner;
    private String updatedAt;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getObjective() {
      return objective;
    }

    public void setObjective(String objective) {
      this.objective = objective;
    }

    public String getDatasetId() {
      return datasetId;
    }

    public void setDatasetId(String datasetId) {
      this.datasetId = datasetId;
    }

    public String getDatasetName() {
      return datasetName;
    }

    public void setDatasetName(String datasetName) {
      this.datasetName = datasetName;
    }

    public String getYoloVersion() {
      return yoloVersion;
    }

    public void setYoloVersion(String yoloVersion) {
      this.yoloVersion = yoloVersion;
    }

    public int getImageSize() {
      return imageSize;
    }

    public void setImageSize(int imageSize) {
      this.imageSize = imageSize;
    }

    public int getEpochs() {
      return epochs;
    }

    public void setEpochs(int epochs) {
      this.epochs = epochs;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public String getOptimizer() {
      return optimizer;
    }

    public void setOptimizer(String optimizer) {
      this.optimizer = optimizer;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getOwner() {
      return owner;
    }

    public void setOwner(String owner) {
      this.owner = owner;
    }

    public String getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
      this.updatedAt = updatedAt;
    }
  }

  public static class TrainingJob {
    private String id;
    private String projectId;
    private String projectName;
    private String datasetName;
    private String status;
    private double map50;
    private double precisionScore;
    private double recallScore;
    private double loss;
    private int currentEpoch;
    private int totalEpochs;
    private String startedAt;
    private String updatedAt;
    private String outputPath;
    private String queueMode;
    private String logPath;
    private String weightsPath;
    private String datasetConfigPath;
    private String commandLine;
    private String failureMessage;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getProjectId() {
      return projectId;
    }

    public void setProjectId(String projectId) {
      this.projectId = projectId;
    }

    public String getProjectName() {
      return projectName;
    }

    public void setProjectName(String projectName) {
      this.projectName = projectName;
    }

    public String getDatasetName() {
      return datasetName;
    }

    public void setDatasetName(String datasetName) {
      this.datasetName = datasetName;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public double getMap50() {
      return map50;
    }

    public void setMap50(double map50) {
      this.map50 = map50;
    }

    public double getPrecisionScore() {
      return precisionScore;
    }

    public void setPrecisionScore(double precisionScore) {
      this.precisionScore = precisionScore;
    }

    public double getRecallScore() {
      return recallScore;
    }

    public void setRecallScore(double recallScore) {
      this.recallScore = recallScore;
    }

    public double getLoss() {
      return loss;
    }

    public void setLoss(double loss) {
      this.loss = loss;
    }

    public int getCurrentEpoch() {
      return currentEpoch;
    }

    public void setCurrentEpoch(int currentEpoch) {
      this.currentEpoch = currentEpoch;
    }

    public int getTotalEpochs() {
      return totalEpochs;
    }

    public void setTotalEpochs(int totalEpochs) {
      this.totalEpochs = totalEpochs;
    }

    public String getStartedAt() {
      return startedAt;
    }

    public void setStartedAt(String startedAt) {
      this.startedAt = startedAt;
    }

    public String getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
      this.updatedAt = updatedAt;
    }

    public String getOutputPath() {
      return outputPath;
    }

    public void setOutputPath(String outputPath) {
      this.outputPath = outputPath;
    }

    public String getQueueMode() {
      return queueMode;
    }

    public void setQueueMode(String queueMode) {
      this.queueMode = queueMode;
    }

    public String getLogPath() {
      return logPath;
    }

    public void setLogPath(String logPath) {
      this.logPath = logPath;
    }

    public String getWeightsPath() {
      return weightsPath;
    }

    public void setWeightsPath(String weightsPath) {
      this.weightsPath = weightsPath;
    }

    public String getDatasetConfigPath() {
      return datasetConfigPath;
    }

    public void setDatasetConfigPath(String datasetConfigPath) {
      this.datasetConfigPath = datasetConfigPath;
    }

    public String getCommandLine() {
      return commandLine;
    }

    public void setCommandLine(String commandLine) {
      this.commandLine = commandLine;
    }

    public String getFailureMessage() {
      return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
      this.failureMessage = failureMessage;
    }
  }

  public static class ModelArtifact {
    private String id;
    private String name;
    private String sourceJobId;
    private String projectName;
    private String yoloVersion;
    private String status;
    private String exportFormat;
    private double map50;
    private String filePath;
    private String sourceModelPath;
    private String sourceModelFormat;
    private String createdAt;
    private String deploymentTarget;
    private String packageVariant;
    private String packageStatus;
    private String packageMessage;
    private String packageDir;
    private String packageArchivePath;
    private String packageManifestPath;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getSourceJobId() {
      return sourceJobId;
    }

    public void setSourceJobId(String sourceJobId) {
      this.sourceJobId = sourceJobId;
    }

    public String getProjectName() {
      return projectName;
    }

    public void setProjectName(String projectName) {
      this.projectName = projectName;
    }

    public String getYoloVersion() {
      return yoloVersion;
    }

    public void setYoloVersion(String yoloVersion) {
      this.yoloVersion = yoloVersion;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getExportFormat() {
      return exportFormat;
    }

    public void setExportFormat(String exportFormat) {
      this.exportFormat = exportFormat;
    }

    public double getMap50() {
      return map50;
    }

    public void setMap50(double map50) {
      this.map50 = map50;
    }

    public String getFilePath() {
      return filePath;
    }

    public void setFilePath(String filePath) {
      this.filePath = filePath;
    }

    public String getSourceModelPath() {
      return sourceModelPath;
    }

    public void setSourceModelPath(String sourceModelPath) {
      this.sourceModelPath = sourceModelPath;
    }

    public String getSourceModelFormat() {
      return sourceModelFormat;
    }

    public void setSourceModelFormat(String sourceModelFormat) {
      this.sourceModelFormat = sourceModelFormat;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(String createdAt) {
      this.createdAt = createdAt;
    }

    public String getDeploymentTarget() {
      return deploymentTarget;
    }

    public void setDeploymentTarget(String deploymentTarget) {
      this.deploymentTarget = deploymentTarget;
    }

    public String getPackageVariant() {
      return packageVariant;
    }

    public void setPackageVariant(String packageVariant) {
      this.packageVariant = packageVariant;
    }

    public String getPackageStatus() {
      return packageStatus;
    }

    public void setPackageStatus(String packageStatus) {
      this.packageStatus = packageStatus;
    }

    public String getPackageMessage() {
      return packageMessage;
    }

    public void setPackageMessage(String packageMessage) {
      this.packageMessage = packageMessage;
    }

    public String getPackageDir() {
      return packageDir;
    }

    public void setPackageDir(String packageDir) {
      this.packageDir = packageDir;
    }

    public String getPackageArchivePath() {
      return packageArchivePath;
    }

    public void setPackageArchivePath(String packageArchivePath) {
      this.packageArchivePath = packageArchivePath;
    }

    public String getPackageManifestPath() {
      return packageManifestPath;
    }

    public void setPackageManifestPath(String packageManifestPath) {
      this.packageManifestPath = packageManifestPath;
    }
  }

  public static class GemmaConversation {
    private String id;
    private String prompt;
    private String response;
    private String focus;
    private String createdAt;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getPrompt() {
      return prompt;
    }

    public void setPrompt(String prompt) {
      this.prompt = prompt;
    }

    public String getResponse() {
      return response;
    }

    public void setResponse(String response) {
      this.response = response;
    }

    public String getFocus() {
      return focus;
    }

    public void setFocus(String focus) {
      this.focus = focus;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(String createdAt) {
      this.createdAt = createdAt;
    }
  }

  public static class QuickStartSession {
    private String id;
    private String targetDescription;
    private String datasetId;
    private String datasetName;
    private String projectId;
    private String projectName;
    private String jobId;
    private String uploadPath;
    private int imageCount;
    private int labeledImageCount;
    private boolean readyForTraining;
    private boolean autoStarted;
    private String status;
    private String objective;
    private String warning;
    private String nextAction;
    private String gemmaSummary;
    private List<String> suggestedClasses = new ArrayList<String>();
    private String createdAt;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getTargetDescription() {
      return targetDescription;
    }

    public void setTargetDescription(String targetDescription) {
      this.targetDescription = targetDescription;
    }

    public String getDatasetId() {
      return datasetId;
    }

    public void setDatasetId(String datasetId) {
      this.datasetId = datasetId;
    }

    public String getDatasetName() {
      return datasetName;
    }

    public void setDatasetName(String datasetName) {
      this.datasetName = datasetName;
    }

    public String getProjectId() {
      return projectId;
    }

    public void setProjectId(String projectId) {
      this.projectId = projectId;
    }

    public String getProjectName() {
      return projectName;
    }

    public void setProjectName(String projectName) {
      this.projectName = projectName;
    }

    public String getJobId() {
      return jobId;
    }

    public void setJobId(String jobId) {
      this.jobId = jobId;
    }

    public String getUploadPath() {
      return uploadPath;
    }

    public void setUploadPath(String uploadPath) {
      this.uploadPath = uploadPath;
    }

    public int getImageCount() {
      return imageCount;
    }

    public void setImageCount(int imageCount) {
      this.imageCount = imageCount;
    }

    public int getLabeledImageCount() {
      return labeledImageCount;
    }

    public void setLabeledImageCount(int labeledImageCount) {
      this.labeledImageCount = labeledImageCount;
    }

    public boolean isReadyForTraining() {
      return readyForTraining;
    }

    public void setReadyForTraining(boolean readyForTraining) {
      this.readyForTraining = readyForTraining;
    }

    public boolean isAutoStarted() {
      return autoStarted;
    }

    public void setAutoStarted(boolean autoStarted) {
      this.autoStarted = autoStarted;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getObjective() {
      return objective;
    }

    public void setObjective(String objective) {
      this.objective = objective;
    }

    public String getWarning() {
      return warning;
    }

    public void setWarning(String warning) {
      this.warning = warning;
    }

    public String getNextAction() {
      return nextAction;
    }

    public void setNextAction(String nextAction) {
      this.nextAction = nextAction;
    }

    public String getGemmaSummary() {
      return gemmaSummary;
    }

    public void setGemmaSummary(String gemmaSummary) {
      this.gemmaSummary = gemmaSummary;
    }

    public List<String> getSuggestedClasses() {
      return suggestedClasses;
    }

    public void setSuggestedClasses(List<String> suggestedClasses) {
      this.suggestedClasses = suggestedClasses;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(String createdAt) {
      this.createdAt = createdAt;
    }
  }

  public static class TimelineEntry {
    private String id;
    private String title;
    private String description;
    private String at;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getAt() {
      return at;
    }

    public void setAt(String at) {
      this.at = at;
    }
  }
}
