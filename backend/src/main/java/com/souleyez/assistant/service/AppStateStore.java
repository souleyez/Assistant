package com.souleyez.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.souleyez.assistant.domain.AppState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AppStateStore {
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
      .enable(SerializationFeature.INDENT_OUTPUT);
  private final Path stateFile = Paths.get("data", "runtime", "assistant-state.json");
  private final Path uploadsDir = Paths.get("data", "runtime", "uploads");
  private AppState state;

  @PostConstruct
  public synchronized void init() throws IOException {
    Files.createDirectories(stateFile.getParent());
    Files.createDirectories(uploadsDir);
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

  public synchronized AppState.Library createLibrary(String name) throws IOException {
    AppState.Library library = new AppState.Library();
    library.setKey(slugify(name) + "-" + shortId());
    library.setLabel(name);
    library.setDocumentCount(0);
    state.getLibraries().add(0, library);
    addTimeline("新建知识库", name + " 已加入文档中心。");
    save();
    return library;
  }

  public synchronized AppState.DatasourceDefinition upsertDatasource(AppState.DatasourceDefinition incoming) throws IOException {
    if (!StringUtils.hasText(incoming.getName())) {
      throw new IllegalArgumentException("数据源名称不能为空");
    }
    if (incoming.getTargetLibraryKeys() == null || incoming.getTargetLibraryKeys().isEmpty()) {
      throw new IllegalArgumentException("至少选择一个知识库");
    }

    AppState.DatasourceDefinition target = null;
    if (StringUtils.hasText(incoming.getId())) {
      for (AppState.DatasourceDefinition item : state.getDatasources()) {
        if (incoming.getId().equals(item.getId())) {
          target = item;
          break;
        }
      }
    }

    if (target == null) {
      target = new AppState.DatasourceDefinition();
      target.setId("ds-" + shortId());
      target.setStatus("draft");
      state.getDatasources().add(0, target);
    }

    target.setName(incoming.getName());
    target.setKind(incoming.getKind());
    target.setEndpoint(incoming.getEndpoint());
    target.setTargetLibraryKeys(new ArrayList<String>(incoming.getTargetLibraryKeys()));
    target.setUpdatedAt(now());
    addTimeline("保存数据源", target.getName() + " 已保存到工作台。");
    save();
    return target;
  }

  public synchronized void changeDatasourceStatus(String id, String action) throws IOException {
    AppState.DatasourceDefinition target = requireDatasource(id);
    if ("activate".equals(action)) {
      target.setStatus("active");
      addTimeline("启用数据源", target.getName() + " 已启用。");
    } else if ("pause".equals(action)) {
      target.setStatus("paused");
      addTimeline("暂停数据源", target.getName() + " 已暂停。");
    } else if ("run".equals(action)) {
      target.setStatus("active");
      AppState.DatasourceRun run = new AppState.DatasourceRun();
      run.setId("run-" + shortId());
      run.setDatasourceId(target.getId());
      run.setDatasourceName(target.getName());
      run.setStatus("success");
      run.setCapturedCount(2 + (int) (Math.random() * 6));
      run.setStartedAt(now());
      state.getDatasourceRuns().add(0, run);
      addTimeline("执行数据源", target.getName() + " 已完成一次采集。");
    }
    target.setUpdatedAt(now());
    save();
  }

  public synchronized void deleteDatasource(String id) throws IOException {
    Iterator<AppState.DatasourceDefinition> iterator = state.getDatasources().iterator();
    while (iterator.hasNext()) {
      AppState.DatasourceDefinition item = iterator.next();
      if (id.equals(item.getId())) {
        iterator.remove();
        addTimeline("删除数据源", item.getName() + " 已从工作台移除。");
        break;
      }
    }
    save();
  }

  public synchronized void deleteRun(String id) throws IOException {
    Iterator<AppState.DatasourceRun> iterator = state.getDatasourceRuns().iterator();
    while (iterator.hasNext()) {
      AppState.DatasourceRun item = iterator.next();
      if (id.equals(item.getId())) {
        iterator.remove();
        addTimeline("删除运行记录", item.getDatasourceName() + " 的运行记录已删除。");
        break;
      }
    }
    save();
  }

  public synchronized AppState.ReportTemplate createTemplate(String label, String description, String sourceType) throws IOException {
    if (!StringUtils.hasText(label)) {
      throw new IllegalArgumentException("模板名称不能为空");
    }
    AppState.ReportTemplate template = new AppState.ReportTemplate();
    template.setKey("tpl-" + shortId());
    template.setLabel(label);
    template.setDescription(description);
    template.setSourceType(sourceType);
    template.setUpdatedAt(now());
    state.getTemplates().add(0, template);
    addTimeline("创建模板", label + " 已加入报表中心。");
    save();
    return template;
  }

  public synchronized AppState.ReportReference addTemplateLink(String templateKey, String label, String url) throws IOException {
    AppState.ReportTemplate template = requireTemplate(templateKey);
    AppState.ReportReference reference = new AppState.ReportReference();
    reference.setId("ref-" + shortId());
    reference.setName(StringUtils.hasText(label) ? label : url);
    reference.setUrl(url);
    reference.setSourceType("web-link");
    reference.setUploadedAt(now());
    template.getReferences().add(0, reference);
    template.setUpdatedAt(now());
    addTimeline("模板链接已接入", template.getLabel() + " 新增网页参考。");
    save();
    return reference;
  }

  public synchronized AppState.ReportReference addTemplateFile(String templateKey, MultipartFile file) throws IOException {
    AppState.ReportTemplate template = requireTemplate(templateKey);
    String fileName = shortId() + "-" + file.getOriginalFilename();
    Path storedPath = uploadsDir.resolve(fileName);
    try (InputStream inputStream = file.getInputStream()) {
      Files.copy(inputStream, storedPath, StandardCopyOption.REPLACE_EXISTING);
    }

    AppState.ReportReference reference = new AppState.ReportReference();
    reference.setId("ref-" + shortId());
    reference.setName(file.getOriginalFilename());
    reference.setFilePath(storedPath.toString());
    reference.setSourceType("file");
    reference.setSize(file.getSize());
    reference.setUploadedAt(now());
    template.getReferences().add(0, reference);
    template.setUpdatedAt(now());
    addTimeline("模板文件已上传", template.getLabel() + " 新增文件参考。");
    save();
    return reference;
  }

  public synchronized void deleteTemplate(String key) throws IOException {
    Iterator<AppState.ReportTemplate> iterator = state.getTemplates().iterator();
    while (iterator.hasNext()) {
      AppState.ReportTemplate item = iterator.next();
      if (key.equals(item.getKey())) {
        deleteFiles(item.getReferences());
        iterator.remove();
        addTimeline("删除模板", item.getLabel() + " 已删除。");
        break;
      }
    }
    save();
  }

  public synchronized void deleteTemplateReference(String templateKey, String id) throws IOException {
    AppState.ReportTemplate template = requireTemplate(templateKey);
    Iterator<AppState.ReportReference> iterator = template.getReferences().iterator();
    while (iterator.hasNext()) {
      AppState.ReportReference reference = iterator.next();
      if (id.equals(reference.getId())) {
        deleteFile(reference);
        iterator.remove();
        template.setUpdatedAt(now());
        addTimeline("删除模板引用", template.getLabel() + " 移除了一个参考项。");
        break;
      }
    }
    save();
  }

  public synchronized AppState.ReportReference requireTemplateReference(String templateKey, String id) {
    AppState.ReportTemplate template = requireTemplate(templateKey);
    for (AppState.ReportReference reference : template.getReferences()) {
      if (id.equals(reference.getId())) {
        return reference;
      }
    }
    throw new IllegalArgumentException("模板引用不存在");
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

  private AppState.DatasourceDefinition requireDatasource(String id) {
    for (AppState.DatasourceDefinition item : state.getDatasources()) {
      if (id.equals(item.getId())) {
        return item;
      }
    }
    throw new IllegalArgumentException("数据源不存在");
  }

  private AppState.ReportTemplate requireTemplate(String key) {
    for (AppState.ReportTemplate item : state.getTemplates()) {
      if (key.equals(item.getKey())) {
        return item;
      }
    }
    throw new IllegalArgumentException("模板不存在");
  }

  private void addTimeline(String title, String description) {
    AppState.TimelineEntry entry = new AppState.TimelineEntry();
    entry.setId("tl-" + shortId());
    entry.setTitle(title);
    entry.setDescription(description);
    entry.setAt(now());
    state.getTimeline().add(0, entry);
  }

  private void deleteFiles(List<AppState.ReportReference> references) {
    for (AppState.ReportReference reference : references) {
      deleteFile(reference);
    }
  }

  private void deleteFile(AppState.ReportReference reference) {
    if (!StringUtils.hasText(reference.getFilePath())) {
      return;
    }
    try {
      Files.deleteIfExists(Paths.get(reference.getFilePath()));
    } catch (IOException ignored) {
      // ignore best-effort cleanup
    }
  }

  private String now() {
    return Instant.now().toString();
  }

  private String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private String slugify(String value) {
    return value.trim().toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-").replaceAll("(^-|-$)", "");
  }

  private AppState seedState() {
    AppState appState = new AppState();

    AppState.OpenClawState openClawState = new AppState.OpenClawState();
    openClawState.setInstalled(false);
    openClawState.setRunning(false);
    openClawState.setGatewayUrl("http://127.0.0.1:18789");

    AppState.ModelState modelState = new AppState.ModelState();
    modelState.setOpenclaw(openClawState);
    modelState.setCurrentModel("gpt-4.1 / local bridge pending");
    modelState.setProviders(Arrays.asList("OpenAI", "OpenClaw", "Custom HTTP"));
    appState.setModel(modelState);

    AppState.Library docs = library("ops-docs", "运营资料库", 2);
    AppState.Library reports = library("report-templates", "报表模板库", 1);
    AppState.Library tenders = library("tender-center", "标书中心", 1);
    appState.setLibraries(new ArrayList<AppState.Library>(Arrays.asList(docs, reports, tenders)));

    appState.setDocuments(new ArrayList<AppState.DocumentItem>(Arrays.asList(
        document("doc-1", "2026-Q1-库存分析.xlsx", docs, "xlsx", "parsed"),
        document("doc-2", "门店客流复盘.pdf", docs, "pdf", "parsed"),
        document("doc-3", "医院投标模板.docx", tenders, "docx", "queued"),
        document("doc-4", "Divoom 汇报模板.pptx", reports, "pptx", "parsed")
    )));

    AppState.DatasourceDefinition datasource = new AppState.DatasourceDefinition();
    datasource.setId("ds-demo-1");
    datasource.setName("京东店铺公开页");
    datasource.setKind("web");
    datasource.setEndpoint("https://example.com/shop");
    datasource.setStatus("active");
    datasource.setTargetLibraryKeys(new ArrayList<String>(Collections.singletonList(docs.getKey())));
    datasource.setUpdatedAt(now());
    appState.setDatasources(new ArrayList<AppState.DatasourceDefinition>(Collections.singletonList(datasource)));

    AppState.DatasourceRun run = new AppState.DatasourceRun();
    run.setId("run-demo-1");
    run.setDatasourceId(datasource.getId());
    run.setDatasourceName(datasource.getName());
    run.setStatus("success");
    run.setCapturedCount(5);
    run.setStartedAt(now());
    appState.setDatasourceRuns(new ArrayList<AppState.DatasourceRun>(Collections.singletonList(run)));

    AppState.BotItem bot = new AppState.BotItem();
    bot.setId("bot-1");
    bot.setName("企业微信报表机器人");
    bot.setChannel("wecom");
    bot.setStatus("connected");
    bot.setBoundLibraries(new ArrayList<String>(Arrays.asList(docs.getKey(), reports.getKey())));
    appState.setBots(new ArrayList<AppState.BotItem>(Collections.singletonList(bot)));

    AppState.ReportTemplate template = new AppState.ReportTemplate();
    template.setKey("tpl-demo-1");
    template.setLabel("月度经营复盘");
    template.setDescription("适用于管理层经营总结与问题回顾。");
    template.setSourceType("file");
    template.setUpdatedAt(now());
    appState.setTemplates(new ArrayList<AppState.ReportTemplate>(Collections.singletonList(template)));

    AppState.OutputRecord output = new AppState.OutputRecord();
    output.setId("out-1");
    output.setTitle("Q1 经营复盘汇总");
    output.setFormat("pptx");
    output.setTemplateLabel(template.getLabel());
    output.setCreatedAt(now());
    appState.setOutputRecords(new ArrayList<AppState.OutputRecord>(Collections.singletonList(output)));

    appState.setTimeline(new ArrayList<AppState.TimelineEntry>());
    addSeedTimeline(appState, "迁移完成初始化", "Assistant 首版仓库已创建。");
    addSeedTimeline(appState, "导入文档样例", "已生成 4 条文档样例数据。");
    addSeedTimeline(appState, "接通机器人样例", "企业微信报表机器人已接入。");
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

  private AppState.Library library(String key, String label, int documentCount) {
    AppState.Library library = new AppState.Library();
    library.setKey(key);
    library.setLabel(label);
    library.setDocumentCount(documentCount);
    return library;
  }

  private AppState.DocumentItem document(String id, String name, AppState.Library library, String fileType, String status) {
    AppState.DocumentItem item = new AppState.DocumentItem();
    item.setId(id);
    item.setName(name);
    item.setLibraryKey(library.getKey());
    item.setLibraryLabel(library.getLabel());
    item.setFileType(fileType);
    item.setParseStatus(status);
    item.setUpdatedAt(now());
    return item;
  }
}
