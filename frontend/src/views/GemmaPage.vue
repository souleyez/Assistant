<template>
  <section class="page">
    <PageHeader
      eyebrow="Gemma 4"
      title="Gemma 训练助手"
      description="这里承接单机版的策略建议位：数据集体检、训练参数建议、导出部署建议，当前先以内置推理占位。"
    />

    <section class="content-grid">
      <article class="panel">
        <p class="eyebrow">发起建议请求</p>
        <div class="form-grid">
          <select v-model="focus" class="input">
            <option value="training">training</option>
            <option value="dataset">dataset</option>
            <option value="deployment">deployment</option>
          </select>
          <textarea v-model.trim="prompt" class="input input-wide textarea textarea-lg" placeholder="例如：头盔数据集存在逆光和遮挡样本偏少，我应该先怎么调整？" />
        </div>
        <div class="form-actions">
          <button class="button" :disabled="submitting" @click="askGemma">
            {{ submitting ? '生成中...' : '请求建议' }}
          </button>
        </div>
      </article>

      <article class="panel">
        <p class="eyebrow">建议焦点</p>
        <div class="chip-row">
          <span class="chip">dataset QA</span>
          <span class="chip">hyper-params</span>
          <span class="chip">deployment export</span>
          <span class="chip">single-machine workflow</span>
        </div>
      </article>
    </section>

    <section class="panel">
      <p class="eyebrow">对话记录</p>
      <div v-if="items.length" class="conversation-list">
        <article v-for="item in items" :key="item.id" class="conversation-card">
          <div class="conversation-meta">
            <span class="chip">{{ item.focus }}</span>
            <small>{{ formatTime(item.createdAt) }}</small>
          </div>
          <h3>{{ item.prompt }}</h3>
          <pre class="response-block">{{ item.response }}</pre>
        </article>
      </div>
      <EmptyState v-else title="暂无建议" text="发起一次 Gemma 请求后，这里会累积建议记录。" />
    </section>

    <div v-if="message" class="panel success-panel">{{ message }}</div>
    <div v-if="error" class="panel error-panel">{{ error }}</div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { request } from '../api'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const prompt = ref('')
const focus = ref('training')
const submitting = ref(false)
const message = ref('')
const error = ref('')

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function loadGemma() {
  const response = await request('/api/gemma-assistant')
  items.value = response.items
}

async function askGemma() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    await request('/api/gemma-assistant', {
      method: 'POST',
      body: JSON.stringify({
        prompt: prompt.value,
        focus: focus.value,
      }),
    })
    prompt.value = ''
    await loadGemma()
    message.value = 'Gemma 建议已生成。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadGemma)
</script>
