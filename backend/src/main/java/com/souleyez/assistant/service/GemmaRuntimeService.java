package com.souleyez.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class GemmaRuntimeService {
  private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final String ollamaUrl;
  private final String primaryModel;
  private final String fallbackModel;

  public GemmaRuntimeService(
      @Value("${assistant.gemma.ollama-url:http://127.0.0.1:11435}") String ollamaUrl,
      @Value("${assistant.gemma.model:gemma4:e4b}") String model,
      @Value("${assistant.gemma.fallback-model:gemma4:26b}") String fallbackModel,
      @Value("${assistant.gemma.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${assistant.gemma.read-timeout-ms:60000}") int readTimeoutMs
  ) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    this.restTemplate = new RestTemplate(requestFactory);
    this.ollamaUrl = trimTrailingSlash(ollamaUrl);
    this.primaryModel = defaultModelName(model, "gemma4:e4b");
    this.fallbackModel = normalizeFallbackModel(fallbackModel, this.primaryModel);
  }

  public boolean isAvailable() {
    return resolveAvailableModel() != null;
  }

  public String getConfiguredModel() {
    String available = resolveAvailableModel();
    return StringUtils.hasText(available) ? available : primaryModel;
  }

  public QuickStartPlan planQuickStart(String targetDescription,
                                       int imageCount,
                                       int labeledImageCount,
                                       List<String> sampleNames) {
    try {
      String response = generate(buildQuickStartPrompt(targetDescription, imageCount, labeledImageCount, sampleNames), true);
      QuickStartPlan parsed = parseQuickStartPlan(response);
      if (!parsed.getClassNames().isEmpty()) {
        return normalizePlan(parsed, targetDescription, imageCount, labeledImageCount);
      }
    } catch (RuntimeException ignored) {
      // fall back
    }
    return fallbackPlan(targetDescription, imageCount, labeledImageCount);
  }

  public String ask(String prompt, String focus) {
    try {
      return generate(buildAdvicePrompt(prompt, focus), false);
    } catch (RuntimeException exception) {
      return fallbackAdvice(prompt, focus);
    }
  }

  private String generate(String prompt, boolean jsonFormat) {
    RuntimeException lastError = null;
    for (String targetModel : resolveGenerationCandidates()) {
      try {
        return generateWithModel(prompt, jsonFormat, targetModel);
      } catch (RuntimeException exception) {
        lastError = exception;
      }
    }
    if (lastError != null) {
      throw lastError;
    }
    throw new IllegalStateException("No Gemma model is available in Ollama");
  }

  private String generateWithModel(String prompt, boolean jsonFormat, String targetModel) {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("model", targetModel);
    payload.put("prompt", prompt);
    payload.put("stream", Boolean.FALSE);
    if (jsonFormat) {
      payload.put("format", "json");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(payload, headers);
    @SuppressWarnings("unchecked")
    Map<String, Object> response = restTemplate.postForObject(
        ollamaUrl + "/api/generate",
        request,
        Map.class
    );
    if (response == null || !StringUtils.hasText((String) response.get("response"))) {
      throw new IllegalStateException("Gemma returned an empty response");
    }
    return ((String) response.get("response")).trim();
  }

  private List<String> resolveGenerationCandidates() {
    Set<String> availableModels = fetchAvailableModels();
    List<String> candidates = new ArrayList<String>();
    if (availableModels.contains(primaryModel)) {
      candidates.add(primaryModel);
    }
    if (StringUtils.hasText(fallbackModel)
        && !fallbackModel.equals(primaryModel)
        && availableModels.contains(fallbackModel)) {
      candidates.add(fallbackModel);
    }
    if (candidates.isEmpty()) {
      candidates.add(primaryModel);
      if (StringUtils.hasText(fallbackModel) && !fallbackModel.equals(primaryModel)) {
        candidates.add(fallbackModel);
      }
    }
    return candidates;
  }

  private String resolveAvailableModel() {
    Set<String> availableModels = fetchAvailableModels();
    if (availableModels.contains(primaryModel)) {
      return primaryModel;
    }
    if (StringUtils.hasText(fallbackModel) && availableModels.contains(fallbackModel)) {
      return fallbackModel;
    }
    return null;
  }

  private Set<String> fetchAvailableModels() {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = restTemplate.getForObject(ollamaUrl + "/api/tags", Map.class);
      Object rawModels = response == null ? null : response.get("models");
      if (!(rawModels instanceof List<?>)) {
        return Collections.emptySet();
      }
      Set<String> names = new LinkedHashSet<String>();
      for (Object item : (List<?>) rawModels) {
        if (!(item instanceof Map<?, ?>)) {
          continue;
        }
        Object name = ((Map<?, ?>) item).get("name");
        if (name != null && StringUtils.hasText(String.valueOf(name))) {
          names.add(String.valueOf(name).trim());
        }
      }
      return names;
    } catch (RuntimeException exception) {
      return Collections.emptySet();
    }
  }

  private QuickStartPlan parseQuickStartPlan(String raw) {
    String json = raw;
    Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
    if (matcher.find()) {
      json = matcher.group();
    }
    try {
      return objectMapper.readValue(json, QuickStartPlan.class);
    } catch (IOException primaryError) {
      try {
        Map<String, Object> generic = objectMapper.readValue(
            json,
            new TypeReference<Map<String, Object>>() { }
        );
        QuickStartPlan plan = new QuickStartPlan();
        plan.setDatasetName(asString(generic.get("datasetName")));
        plan.setProjectName(asString(generic.get("projectName")));
        plan.setObjective(asString(generic.get("objective")));
        plan.setSummary(asString(generic.get("summary")));
        plan.setYoloVersion(asString(generic.get("yoloVersion")));
        plan.setOptimizer(asString(generic.get("optimizer")));
        plan.setImageSize(asInt(generic.get("imageSize"), 640));
        plan.setEpochs(asInt(generic.get("epochs"), 120));
        plan.setBatchSize(asInt(generic.get("batchSize"), 16));
        plan.setClassNames(extractStringList(generic.get("classNames")));
        plan.setDetectionPrompts(extractStringList(generic.get("detectionPrompts")));
        return plan;
      } catch (IOException secondaryError) {
        throw new IllegalStateException("Failed to parse Gemma quick-start plan", secondaryError);
      }
    }
  }

  private QuickStartPlan normalizePlan(QuickStartPlan plan,
                                       String targetDescription,
                                       int imageCount,
                                       int labeledImageCount) {
    QuickStartPlan normalized = new QuickStartPlan();
    normalized.setClassNames(normalizeClassNames(plan.getClassNames(), targetDescription));
    normalized.setDetectionPrompts(normalizeDetectionPrompts(plan.getDetectionPrompts(), normalized.getClassNames()));
    normalized.setDatasetName(defaultText(plan.getDatasetName(), buildName(normalized.getClassNames(), "dataset")));
    normalized.setProjectName(defaultText(plan.getProjectName(), buildName(normalized.getClassNames(), "project")));
    normalized.setObjective(defaultText(plan.getObjective(), buildObjective(targetDescription)));
    normalized.setSummary(defaultText(plan.getSummary(), fallbackSummary(imageCount, labeledImageCount)));
    normalized.setYoloVersion(defaultText(plan.getYoloVersion(), "YOLOv11m"));
    normalized.setImageSize(bound(plan.getImageSize(), 640, 320, 1280));
    normalized.setEpochs(bound(plan.getEpochs(), 120, 20, 500));
    normalized.setBatchSize(bound(plan.getBatchSize(), 16, 1, 128));
    normalized.setOptimizer(defaultText(plan.getOptimizer(), "SGD"));
    return normalized;
  }

  private QuickStartPlan fallbackPlan(String targetDescription, int imageCount, int labeledImageCount) {
    QuickStartPlan plan = new QuickStartPlan();
    List<String> classes = extractClasses(targetDescription);
    plan.setClassNames(classes);
    plan.setDetectionPrompts(new ArrayList<String>(classes));
    plan.setDatasetName(buildName(classes, "dataset"));
    plan.setProjectName(buildName(classes, "project"));
    plan.setObjective(buildObjective(targetDescription));
    plan.setSummary(fallbackSummary(imageCount, labeledImageCount));
    plan.setYoloVersion("YOLOv11m");
    plan.setImageSize(imageCount > 400 ? 960 : 640);
    plan.setEpochs(labeledImageCount == imageCount && imageCount > 0 ? 120 : 60);
    plan.setBatchSize(16);
    plan.setOptimizer("SGD");
    return plan;
  }

  private String buildQuickStartPrompt(String targetDescription,
                                       int imageCount,
                                       int labeledImageCount,
                                       List<String> sampleNames) {
    return "你是单机 YOLO 训练平台的 Quick Start 解析器。"
        + "用户只上传图片或 zip，并用自然语言描述要捕获的目标。"
        + "请只输出 JSON，对应字段必须齐全，不要输出 markdown。\n"
        + "JSON schema:\n"
        + "{\n"
        + "  \"datasetName\": \"\",\n"
        + "  \"projectName\": \"\",\n"
        + "  \"objective\": \"\",\n"
        + "  \"summary\": \"\",\n"
        + "  \"classNames\": [\"\"],\n"
        + "  \"detectionPrompts\": [\"\"],\n"
        + "  \"yoloVersion\": \"YOLOv11m\",\n"
        + "  \"imageSize\": 640,\n"
        + "  \"epochs\": 120,\n"
        + "  \"batchSize\": 16,\n"
        + "  \"optimizer\": \"SGD\"\n"
        + "}\n"
        + "规则:\n"
        + "1. classNames 只保留 1 到 4 个真正需要检测的类别名，可用中文。\n"
        + "2. detectionPrompts 必须与 classNames 一一对应，输出适合开放词汇检测的英文短语。\n"
        + "3. datasetName 和 projectName 要简短明确。\n"
        + "4. objective 要直接说明训练目标。\n"
        + "5. summary 要告诉用户当前数据是否适合直接开训。\n"
        + "6. 如果图片没有完整标注，要在 summary 中直接说需要补标注。\n\n"
        + "用户描述: " + targetDescription + "\n"
        + "图片数量: " + imageCount + "\n"
        + "已发现标注数量: " + labeledImageCount + "\n"
        + "样本文件名: " + sampleNames + "\n";
  }

  private String buildAdvicePrompt(String prompt, String focus) {
    return "你是 Gemma 4 单机 YOLO 训练助手。"
        + "请直接给 3 到 5 条中文建议，不要输出前言，不要输出 markdown 标题。"
        + "关注方向: " + defaultText(focus, "training") + "\n"
        + "用户问题: " + prompt;
  }

  private String fallbackAdvice(String prompt, String focus) {
    String normalized = defaultText(prompt, "").toLowerCase(Locale.ROOT);
    if (normalized.contains("标注") || normalized.contains("class")) {
      return "- 先确认类别定义是否互斥\n- 抽检 50 张样本，优先修正漏框与错类\n- 类别边界不清时，先缩成 1 到 2 个核心类";
    }
    if ("dataset".equals(focus)) {
      return "- 先看类别是否失衡\n- 先确认 train/val 来源一致\n- 没有完整标注前不要直接开真实训练";
    }
    if ("deployment".equals(focus)) {
      return "- 先固定输入尺寸\n- 同时保留 best.pt 和导出模型\n- 记录阈值建议，避免上线后误检失控";
    }
    return "- 先用 YOLOv11m 做基线\n- 先确保标注闭环，再追求更高精度\n- 以 mAP50、Precision、Recall 一起看，不要只盯单一指标";
  }

  private List<String> extractClasses(String description) {
    String normalized = defaultText(description, "")
        .replace("以及", ",")
        .replace("和", ",")
        .replace("及", ",")
        .replace("、", ",")
        .replace("，", ",")
        .replace("。", ",")
        .replace("/", ",");
    String[] parts = normalized.split("[,\\n]+");
    Set<String> classes = new LinkedHashSet<String>();
    List<String> stopWords = Arrays.asList("检测", "识别", "抓拍", "捕获", "目标", "对象", "画面", "图片", "我要", "需要", "帮我", "训练");
    for (String part : parts) {
      String candidate = part.trim();
      if (!StringUtils.hasText(candidate)) {
        continue;
      }
      for (String stopWord : stopWords) {
        candidate = candidate.replace(stopWord, "").trim();
      }
      if (StringUtils.hasText(candidate)) {
        classes.add(candidate);
      }
      if (classes.size() >= 4) {
        break;
      }
    }
    if (classes.isEmpty()) {
      classes.add("target");
    }
    return new ArrayList<String>(classes);
  }

  private List<String> normalizeClassNames(List<String> source, String description) {
    List<String> normalized = new ArrayList<String>();
    if (source != null) {
      for (String item : source) {
        if (StringUtils.hasText(item)) {
          normalized.add(item.trim());
        }
      }
    }
    if (normalized.isEmpty()) {
      normalized.addAll(extractClasses(description));
    }
    if (normalized.size() > 4) {
      normalized = normalized.subList(0, 4);
    }
    return normalized;
  }

  private List<String> normalizeDetectionPrompts(List<String> prompts, List<String> classNames) {
    List<String> normalized = new ArrayList<String>();
    if (prompts != null) {
      for (String item : prompts) {
        if (StringUtils.hasText(item)) {
          normalized.add(item.trim());
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
      normalized = normalized.subList(0, classNames.size());
    }
    return new ArrayList<String>(normalized);
  }

  private String buildName(List<String> classes, String suffix) {
    String seed = classes.isEmpty() ? "target" : classes.get(0);
    String compact = seed.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+", "").trim();
    if (!StringUtils.hasText(compact)) {
      compact = "target";
    }
    return compact + "-" + suffix;
  }

  private String buildObjective(String description) {
    if (StringUtils.hasText(description)) {
      return "围绕“" + description.trim() + "”构建首版检测基线，并尽快验证可训练性。";
    }
    return "建立首版检测基线并验证数据质量。";
  }

  private String fallbackSummary(int imageCount, int labeledImageCount) {
    if (imageCount > 0 && labeledImageCount == imageCount) {
      return "已检测到完整图片与标注，可直接生成训练项目并进入训练。";
    }
    return "已接收图片并生成训练方案，但当前标注不完整，需补齐 YOLO 标签后再进行真实训练。";
  }

  private int bound(int value, int fallback, int min, int max) {
    int effective = value > 0 ? value : fallback;
    return Math.max(min, Math.min(max, effective));
  }

  private String defaultText(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private String trimTrailingSlash(String value) {
    if (!StringUtils.hasText(value)) {
      return "http://127.0.0.1:11435";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String defaultModelName(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private String normalizeFallbackModel(String value, String primary) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String normalized = value.trim();
    return normalized.equals(primary) ? null : normalized;
  }

  private String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private int asInt(Object value, int fallback) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value != null) {
      try {
        return Integer.parseInt(String.valueOf(value));
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private List<String> extractStringList(Object value) {
    if (!(value instanceof List<?>)) {
      return Collections.emptyList();
    }
    List<String> items = new ArrayList<String>();
    for (Object item : (List<?>) value) {
      if (item != null && StringUtils.hasText(String.valueOf(item))) {
        items.add(String.valueOf(item).trim());
      }
    }
    return items;
  }

  public static class QuickStartPlan {
    private String datasetName;
    private String projectName;
    private String objective;
    private String summary;
    private List<String> classNames = new ArrayList<String>();
    private List<String> detectionPrompts = new ArrayList<String>();
    private String yoloVersion;
    private int imageSize;
    private int epochs;
    private int batchSize;
    private String optimizer;

    public String getDatasetName() {
      return datasetName;
    }

    public void setDatasetName(String datasetName) {
      this.datasetName = datasetName;
    }

    public String getProjectName() {
      return projectName;
    }

    public void setProjectName(String projectName) {
      this.projectName = projectName;
    }

    public String getObjective() {
      return objective;
    }

    public void setObjective(String objective) {
      this.objective = objective;
    }

    public String getSummary() {
      return summary;
    }

    public void setSummary(String summary) {
      this.summary = summary;
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
  }
}
