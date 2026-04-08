package com.souleyez.assistant.domain;

import java.util.ArrayList;
import java.util.List;

public class AppState {
  private ModelState model;
  private List<Library> libraries = new ArrayList<Library>();
  private List<DocumentItem> documents = new ArrayList<DocumentItem>();
  private List<DatasourceDefinition> datasources = new ArrayList<DatasourceDefinition>();
  private List<DatasourceRun> datasourceRuns = new ArrayList<DatasourceRun>();
  private List<BotItem> bots = new ArrayList<BotItem>();
  private List<ReportTemplate> templates = new ArrayList<ReportTemplate>();
  private List<OutputRecord> outputRecords = new ArrayList<OutputRecord>();
  private List<TimelineEntry> timeline = new ArrayList<TimelineEntry>();

  public ModelState getModel() {
    return model;
  }

  public void setModel(ModelState model) {
    this.model = model;
  }

  public List<Library> getLibraries() {
    return libraries;
  }

  public void setLibraries(List<Library> libraries) {
    this.libraries = libraries;
  }

  public List<DocumentItem> getDocuments() {
    return documents;
  }

  public void setDocuments(List<DocumentItem> documents) {
    this.documents = documents;
  }

  public List<DatasourceDefinition> getDatasources() {
    return datasources;
  }

  public void setDatasources(List<DatasourceDefinition> datasources) {
    this.datasources = datasources;
  }

  public List<DatasourceRun> getDatasourceRuns() {
    return datasourceRuns;
  }

  public void setDatasourceRuns(List<DatasourceRun> datasourceRuns) {
    this.datasourceRuns = datasourceRuns;
  }

  public List<BotItem> getBots() {
    return bots;
  }

  public void setBots(List<BotItem> bots) {
    this.bots = bots;
  }

  public List<ReportTemplate> getTemplates() {
    return templates;
  }

  public void setTemplates(List<ReportTemplate> templates) {
    this.templates = templates;
  }

  public List<OutputRecord> getOutputRecords() {
    return outputRecords;
  }

  public void setOutputRecords(List<OutputRecord> outputRecords) {
    this.outputRecords = outputRecords;
  }

  public List<TimelineEntry> getTimeline() {
    return timeline;
  }

  public void setTimeline(List<TimelineEntry> timeline) {
    this.timeline = timeline;
  }

  public static class ModelState {
    private OpenClawState openclaw;
    private String currentModel;
    private List<String> providers = new ArrayList<String>();

    public OpenClawState getOpenclaw() {
      return openclaw;
    }

    public void setOpenclaw(OpenClawState openclaw) {
      this.openclaw = openclaw;
    }

    public String getCurrentModel() {
      return currentModel;
    }

    public void setCurrentModel(String currentModel) {
      this.currentModel = currentModel;
    }

    public List<String> getProviders() {
      return providers;
    }

    public void setProviders(List<String> providers) {
      this.providers = providers;
    }
  }

  public static class OpenClawState {
    private boolean installed;
    private boolean running;
    private String gatewayUrl;

    public boolean isInstalled() {
      return installed;
    }

    public void setInstalled(boolean installed) {
      this.installed = installed;
    }

    public boolean isRunning() {
      return running;
    }

    public void setRunning(boolean running) {
      this.running = running;
    }

    public String getGatewayUrl() {
      return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
      this.gatewayUrl = gatewayUrl;
    }
  }

  public static class Library {
    private String key;
    private String label;
    private int documentCount;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public int getDocumentCount() {
      return documentCount;
    }

    public void setDocumentCount(int documentCount) {
      this.documentCount = documentCount;
    }
  }

  public static class DocumentItem {
    private String id;
    private String name;
    private String libraryKey;
    private String libraryLabel;
    private String fileType;
    private String parseStatus;
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

    public String getLibraryKey() {
      return libraryKey;
    }

    public void setLibraryKey(String libraryKey) {
      this.libraryKey = libraryKey;
    }

    public String getLibraryLabel() {
      return libraryLabel;
    }

    public void setLibraryLabel(String libraryLabel) {
      this.libraryLabel = libraryLabel;
    }

    public String getFileType() {
      return fileType;
    }

    public void setFileType(String fileType) {
      this.fileType = fileType;
    }

    public String getParseStatus() {
      return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
      this.parseStatus = parseStatus;
    }

    public String getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
      this.updatedAt = updatedAt;
    }
  }

  public static class DatasourceDefinition {
    private String id;
    private String name;
    private String kind;
    private String endpoint;
    private String status;
    private List<String> targetLibraryKeys = new ArrayList<String>();
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

    public String getKind() {
      return kind;
    }

    public void setKind(String kind) {
      this.kind = kind;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public List<String> getTargetLibraryKeys() {
      return targetLibraryKeys;
    }

    public void setTargetLibraryKeys(List<String> targetLibraryKeys) {
      this.targetLibraryKeys = targetLibraryKeys;
    }

    public String getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
      this.updatedAt = updatedAt;
    }
  }

  public static class DatasourceRun {
    private String id;
    private String datasourceId;
    private String datasourceName;
    private String status;
    private int capturedCount;
    private String startedAt;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getDatasourceId() {
      return datasourceId;
    }

    public void setDatasourceId(String datasourceId) {
      this.datasourceId = datasourceId;
    }

    public String getDatasourceName() {
      return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
      this.datasourceName = datasourceName;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public int getCapturedCount() {
      return capturedCount;
    }

    public void setCapturedCount(int capturedCount) {
      this.capturedCount = capturedCount;
    }

    public String getStartedAt() {
      return startedAt;
    }

    public void setStartedAt(String startedAt) {
      this.startedAt = startedAt;
    }
  }

  public static class BotItem {
    private String id;
    private String name;
    private String channel;
    private String status;
    private List<String> boundLibraries = new ArrayList<String>();

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

    public String getChannel() {
      return channel;
    }

    public void setChannel(String channel) {
      this.channel = channel;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public List<String> getBoundLibraries() {
      return boundLibraries;
    }

    public void setBoundLibraries(List<String> boundLibraries) {
      this.boundLibraries = boundLibraries;
    }
  }

  public static class ReportTemplate {
    private String key;
    private String label;
    private String description;
    private String sourceType;
    private String updatedAt;
    private List<ReportReference> references = new ArrayList<ReportReference>();

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

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

    public String getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
      this.updatedAt = updatedAt;
    }

    public List<ReportReference> getReferences() {
      return references;
    }

    public void setReferences(List<ReportReference> references) {
      this.references = references;
    }
  }

  public static class ReportReference {
    private String id;
    private String name;
    private String url;
    private String filePath;
    private String sourceType;
    private long size;
    private String uploadedAt;

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

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getFilePath() {
      return filePath;
    }

    public void setFilePath(String filePath) {
      this.filePath = filePath;
    }

    public String getSourceType() {
      return sourceType;
    }

    public void setSourceType(String sourceType) {
      this.sourceType = sourceType;
    }

    public long getSize() {
      return size;
    }

    public void setSize(long size) {
      this.size = size;
    }

    public String getUploadedAt() {
      return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
      this.uploadedAt = uploadedAt;
    }
  }

  public static class OutputRecord {
    private String id;
    private String title;
    private String format;
    private String templateLabel;
    private String createdAt;

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

    public String getFormat() {
      return format;
    }

    public void setFormat(String format) {
      this.format = format;
    }

    public String getTemplateLabel() {
      return templateLabel;
    }

    public void setTemplateLabel(String templateLabel) {
      this.templateLabel = templateLabel;
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
