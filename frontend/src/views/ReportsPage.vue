<template>
  <section class="page">
    <PageHeader
      eyebrow="Reports"
      title="报表中心"
      description="支持上传模板说明、沉淀参考链接与文件，并保留原项目的可用输出机器人视图。"
    />

    <section class="content-grid">
      <article class="panel">
        <p class="eyebrow">上传模板</p>
        <div class="form-grid">
          <input v-model.trim="draft.label" class="input" placeholder="模板名称" />
          <input v-model.trim="draft.description" class="input" placeholder="模板说明" />
          <input v-model.trim="draft.link" class="input input-wide" placeholder="网页链接，可与文件上传二选一" />
          <input ref="fileInput" class="input input-wide" type="file" @change="handleFileChange" />
        </div>
        <div class="form-actions">
          <button class="button" :disabled="submitting" @click="createTemplate">
            {{ submitting ? '上传中...' : '创建模板' }}
          </button>
        </div>
      </article>

      <article class="panel">
        <p class="eyebrow">可用输出机器人</p>
        <div v-if="bots.length" class="list-stack">
          <div v-for="bot in bots" :key="bot.id" class="list-row">
            <div>
              <strong>{{ bot.name }}</strong>
              <p>{{ bot.channel }}</p>
            </div>
            <span class="chip">{{ bot.status }}</span>
          </div>
        </div>
        <EmptyState v-else title="暂无机器人" text="后端样例数据里还没有机器人时，这里会显示空态。" />
      </article>
    </section>

    <section class="panel table-panel">
      <p class="eyebrow">模板列表</p>
      <table v-if="templates.length" class="table">
        <thead>
          <tr>
            <th>模板</th>
            <th>说明</th>
            <th>引用数</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in templates" :key="item.key">
            <td>{{ item.label }}</td>
            <td>{{ item.description || '-' }}</td>
            <td>{{ item.references.length }}</td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td>
              <button class="button button-danger" @click="deleteTemplate(item.key)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-else title="暂无模板" text="上传一个模板文件或模板链接后，这里会出现记录。" />
    </section>

    <section class="panel table-panel">
      <p class="eyebrow">生成输出</p>
      <table v-if="outputs.length" class="table">
        <thead>
          <tr>
            <th>标题</th>
            <th>格式</th>
            <th>来源模板</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in outputs" :key="item.id">
            <td>{{ item.title }}</td>
            <td>{{ item.format }}</td>
            <td>{{ item.templateLabel }}</td>
            <td>{{ formatTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-else title="暂无输出" text="后续可以继续在 Java 端接入真正的报表生成器。" />
    </section>

    <div v-if="message" class="panel success-panel">{{ message }}</div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { API_BASE, request } from '../api'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'

const fileInput = ref(null)
const selectedFile = ref(null)
const submitting = ref(false)
const message = ref('')
const templates = ref([])
const outputs = ref([])
const bots = ref([])
const draft = ref({
  label: '',
  description: '',
  link: '',
})

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function handleFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
}

async function loadReports() {
  const [reportData, botData] = await Promise.all([
    request('/api/reports'),
    request('/api/bots'),
  ])
  templates.value = reportData.templates
  outputs.value = reportData.outputRecords
  bots.value = botData.items
}

async function createTemplate() {
  if (!draft.value.label) {
    message.value = '模板名称不能为空。'
    return
  }
  if (!draft.value.link && !selectedFile.value) {
    message.value = '文件和链接至少提供一个。'
    return
  }

  submitting.value = true
  message.value = ''
  try {
    const templateResponse = await request('/api/reports/template', {
      method: 'POST',
      body: JSON.stringify({
        label: draft.value.label,
        description: draft.value.description,
        sourceType: selectedFile.value ? 'file' : 'web-link',
      }),
    })

    if (selectedFile.value) {
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      const response = await fetch(`${API_BASE}/api/reports/template-reference?templateKey=${templateResponse.item.key}`, {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) {
        throw new Error('上传模板文件失败')
      }
    }

    if (draft.value.link) {
      await request('/api/reports/template-reference-link', {
        method: 'POST',
        body: JSON.stringify({
          templateKey: templateResponse.item.key,
          label: draft.value.label,
          url: draft.value.link,
        }),
      })
    }

    draft.value = { label: '', description: '', link: '' }
    selectedFile.value = null
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    await loadReports()
    message.value = '模板已创建。'
  } catch (err) {
    message.value = err instanceof Error ? err.message : '模板创建失败'
  } finally {
    submitting.value = false
  }
}

async function deleteTemplate(key) {
  await request(`/api/reports/template/${key}`, { method: 'DELETE' })
  await loadReports()
  message.value = '模板已删除。'
}

onMounted(loadReports)
</script>
