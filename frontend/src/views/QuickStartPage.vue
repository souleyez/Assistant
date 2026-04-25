<template>
  <section class="page">
    <PageHeader
      eyebrow="Quick Start"
      title="上传图片，一句话开训"
      description="最简路径：上传图片或 zip，写一句要抓什么。系统会用 Gemma 4 理解目标，先尝试自动预标注，再自动建数据集和训练项目；标注覆盖完整时会直接进入训练。"
    />

    <section class="content-grid quickstart-grid">
      <article class="panel quickstart-form-panel">
        <p class="eyebrow">一键开训</p>
        <div class="quickstart-form">
          <label class="quickstart-label">
            <span>目标描述</span>
            <textarea
              v-model.trim="targetDescription"
              class="input textarea textarea-lg"
              placeholder="例如：抓拍工地现场未戴安全帽的人，以及画面中的安全帽。"
            />
          </label>

          <label class="quickstart-label">
            <span>上传文件</span>
            <input
              ref="fileInput"
              class="input"
              type="file"
              multiple
              accept=".zip,.jpg,.jpeg,.png,.webp,.bmp,.txt"
              @change="handleFiles"
            />
            <small class="table-subtle">支持 zip、图片；如果同时带上 YOLO `.txt` 标注会优先复用，缺失部分则交给自动预标注补齐。</small>
          </label>

          <div v-if="selectedFiles.length" class="upload-summary">
            <div class="chip-row">
              <span class="chip">{{ selectedFiles.length }} 个文件</span>
              <span class="chip">{{ imageFileCount }} 张图片</span>
              <span class="chip">{{ zipFileCount }} 个 zip</span>
              <span class="chip">{{ uploadTotalSizeLabel }}</span>
            </div>
            <div class="sample-file-list">
              <span v-for="file in selectedFiles.slice(0, 8)" :key="file.name" class="sample-file-chip">{{ file.name }}</span>
            </div>
          </div>

          <label class="quickstart-toggle">
            <input v-model="autoStart" type="checkbox" />
            <span>检测到完整标注时自动启动训练</span>
          </label>

          <div class="form-actions">
            <button class="button" :disabled="submitting || !selectedFiles.length || !targetDescription" @click="submitQuickStart">
              {{ submitting ? '正在上传...' : '上传并生成训练方案' }}
            </button>
            <button class="button button-secondary" :disabled="submitting" @click="clearForm">清空</button>
          </div>
          <small v-if="submitting" class="table-subtle">文件正在上传；上传完成后，Gemma 解析、自动预标注和训练准备会转入后台继续执行。</small>
        </div>
      </article>

      <article class="panel">
        <p class="eyebrow">Runtime</p>
        <div class="list-stack">
          <div class="list-row">
            <div>
              <strong>Gemma 4</strong>
              <p>{{ runtime.gemmaModel || 'unknown' }}</p>
            </div>
            <span class="chip">{{ runtime.gemmaReady ? 'ready' : 'fallback' }}</span>
          </div>
          <div class="list-row">
            <div>
              <strong>接受输入</strong>
              <p>{{ (runtime.accepts || []).join(', ') }}</p>
            </div>
            <span class="chip">quick import</span>
          </div>
          <div class="list-row">
            <div>
              <strong>自动开训</strong>
              <p>只有检测到完整 YOLO 标注时才会自动拉起训练。</p>
            </div>
            <span class="chip">{{ runtime.autoStartWhenLabeled ? 'guarded' : 'off' }}</span>
          </div>
          <div class="list-row">
            <div>
              <strong>自动预标注</strong>
              <p>{{ runtime.autoLabelModel || 'YOLOWorld' }} · conf {{ runtime.autoLabelConfidence ?? 0.2 }}</p>
            </div>
            <span class="chip">{{ runtime.autoLabelEnabled ? 'enabled' : 'off' }}</span>
          </div>
        </div>
      </article>
    </section>

    <section v-if="lastResult" class="panel quickstart-result-panel">
      <div class="quickstart-result-head">
        <div>
          <p class="eyebrow">Latest Result</p>
          <h2>{{ lastResult.item.projectName || '训练方案生成中' }}</h2>
          <p class="page-copy">{{ lastResult.item.objective }}</p>
        </div>
        <span class="chip">{{ lastResult.item.status }}</span>
      </div>

      <div class="chip-row">
        <span v-for="item in lastResult.item.suggestedClasses" :key="item" class="chip">{{ item }}</span>
      </div>

      <section class="dashboard-grid">
        <article class="panel panel-flat">
          <p class="eyebrow">Gemma Summary</p>
          <pre class="response-block">{{ lastResult.item.gemmaSummary }}</pre>
        </article>
        <article class="panel panel-flat">
          <p class="eyebrow">Execution</p>
          <div class="list-stack">
            <div class="list-row" v-if="lastResult.dataset">
              <div>
                <strong>数据集</strong>
                <p>{{ lastResult.dataset.name }}</p>
              </div>
              <span class="chip">{{ lastResult.item.imageCount }} img / {{ lastResult.item.labeledImageCount }} label</span>
            </div>
            <div class="list-row" v-else>
              <div>
                <strong>{{ lastResult.item.datasetId ? '数据集' : '处理状态' }}</strong>
                <p>{{ lastResult.item.datasetId ? lastResult.item.datasetName : lastResult.item.nextAction }}</p>
              </div>
              <span class="chip">{{ lastResult.item.imageCount }} 图片</span>
            </div>
            <div class="list-row" v-if="lastResult.item.autoLabelStatus">
              <div>
                <strong>自动预标注</strong>
                <p>{{ lastResult.item.autoLabelModel || 'YOLOWorld' }} · +{{ lastResult.item.autoLabelCreatedCount }} / 剩余 {{ lastResult.item.autoLabelRemainingCount }}</p>
              </div>
              <span class="chip">{{ lastResult.item.autoLabelStatus }}</span>
            </div>
            <div class="list-row" v-if="lastResult.project">
              <div>
                <strong>训练项目</strong>
                <p>{{ lastResult.project.yoloVersion }} · {{ lastResult.project.epochs }}e · b{{ lastResult.project.batchSize }}</p>
              </div>
              <span class="chip">{{ lastResult.project.status }}</span>
            </div>
            <div class="list-row" v-else-if="lastResult.item.projectId">
              <div>
                <strong>训练项目</strong>
                <p>{{ lastResult.item.projectName }}</p>
              </div>
              <span class="chip">{{ lastResult.item.status }}</span>
            </div>
            <div class="list-row" v-if="lastResult.job">
              <div>
                <strong>训练任务</strong>
                <p>{{ lastResult.job.id }}</p>
              </div>
              <span class="chip">{{ lastResult.job.status }}</span>
            </div>
          </div>
        </article>
      </section>

      <div class="panel note-panel" v-if="lastResult.item.warning || lastResult.item.nextAction">
        <p class="eyebrow">Next Action</p>
        <p class="page-copy">{{ lastResult.item.warning || lastResult.item.nextAction }}</p>
        <p v-if="lastResult.item.autoLabelMessage" class="table-subtle">{{ lastResult.item.autoLabelMessage }}</p>
      </div>

      <div class="form-actions">
        <RouterLink class="button button-secondary" to="/datasets">看数据集</RouterLink>
        <RouterLink class="button button-secondary" to="/projects">看项目</RouterLink>
        <RouterLink class="button" to="/jobs">{{ lastResult.job ? '看训练任务' : '去训练任务页' }}</RouterLink>
      </div>
    </section>

    <section class="panel">
      <p class="eyebrow">Recent Quick Starts</p>
      <div v-if="items.length" class="conversation-list">
        <article v-for="item in items" :key="item.id" class="conversation-card">
          <div class="conversation-meta">
            <span class="chip">{{ item.status }}</span>
            <small>{{ formatTime(item.createdAt) }}</small>
          </div>
          <h3>{{ item.projectName }}</h3>
          <p class="page-copy">{{ item.targetDescription }}</p>
          <div class="chip-row">
            <span class="chip">{{ item.imageCount }} 图片</span>
            <span class="chip">{{ item.labeledImageCount }} 标注</span>
            <span v-if="item.autoLabelStatus" class="chip">auto {{ item.autoLabelStatus }}</span>
            <span v-if="item.autoLabelCreatedCount" class="chip">+{{ item.autoLabelCreatedCount }}</span>
            <span v-for="name in item.suggestedClasses" :key="`${item.id}-${name}`" class="chip">{{ name }}</span>
          </div>
          <p class="table-subtle">{{ item.autoLabelMessage || item.nextAction }}</p>
        </article>
      </div>
      <EmptyState v-else title="暂无一键开训记录" text="上传第一批图片后，这里会显示最近的 Quick Start。" />
    </section>

    <div v-if="message" class="panel success-panel">{{ message }}</div>
    <div v-if="error" class="panel error-panel">{{ error }}</div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { request } from '../api'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'

const fileInput = ref(null)
const items = ref([])
const runtime = ref({
  gemmaReady: false,
  gemmaModel: '',
  accepts: [],
  autoStartWhenLabeled: true,
  autoLabelEnabled: true,
  autoLabelModel: '',
  autoLabelConfidence: 0.2,
})
const selectedFiles = ref([])
const targetDescription = ref('')
const autoStart = ref(true)
const submitting = ref(false)
const message = ref('')
const error = ref('')
const lastResult = ref(null)
const pollTimer = ref(null)

const imageFileCount = computed(() =>
  selectedFiles.value.filter((file) => /\.(jpg|jpeg|png|webp|bmp)$/i.test(file.name)).length
)
const zipFileCount = computed(() =>
  selectedFiles.value.filter((file) => /\.zip$/i.test(file.name)).length
)
const uploadTotalSize = computed(() =>
  selectedFiles.value.reduce((total, file) => total + (file.size || 0), 0)
)
const uploadTotalSizeLabel = computed(() => formatBytes(uploadTotalSize.value))

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function formatBytes(bytes) {
  if (!bytes) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`
}

function handleFiles(event) {
  selectedFiles.value = Array.from(event.target.files || [])
}

function clearForm() {
  targetDescription.value = ''
  selectedFiles.value = []
  autoStart.value = true
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

async function loadQuickStarts() {
  const response = await request('/api/quick-start')
  items.value = response.items || []
  runtime.value = response.runtime || runtime.value
  syncLastResultFromItems()
}

function syncLastResultFromItems() {
  if (!lastResult.value?.item?.id) {
    return
  }
  const latest = items.value.find((item) => item.id === lastResult.value.item.id)
  if (latest) {
    lastResult.value = {
      ...lastResult.value,
      item: latest,
    }
  }
}

async function submitQuickStart() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    const formData = new FormData()
    selectedFiles.value.forEach((file) => formData.append('files', file))
    formData.append('targetDescription', targetDescription.value)
    formData.append('autoStart', String(autoStart.value))
    const response = await request('/api/quick-start', {
      method: 'POST',
      body: formData,
    })
    lastResult.value = response
    await loadQuickStarts()
    if (['uploaded', 'processing', 'extracting', 'gemma-planning', 'auto-labeling'].includes(response.item?.status)) {
      message.value = '上传已接收，系统会在后台解压、解析目标、自动预标注并准备训练；页面会自动刷新状态。'
    } else if (response.job) {
      message.value = 'Gemma 解析和自动预标注已完成，训练任务已自动创建。'
    } else if (response.item?.autoLabelStatus === 'partial') {
      message.value = 'Gemma 解析完成，系统已自动预标注一部分图片，剩余样本待复核。'
    } else if (response.item?.autoLabelStatus) {
      message.value = 'Gemma 解析和自动预标注已完成，数据集和项目已自动建立。'
    } else {
      message.value = 'Gemma 已完成解析，数据集和项目已自动建立。'
    }
    clearForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Quick Start 失败'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadQuickStarts()
  pollTimer.value = window.setInterval(loadQuickStarts, 5000)
})

onBeforeUnmount(() => {
  if (pollTimer.value) {
    window.clearInterval(pollTimer.value)
  }
})
</script>
