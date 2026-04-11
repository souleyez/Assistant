<template>
  <section class="page">
    <PageHeader
      eyebrow="Projects"
      title="训练项目"
      description="把数据集、YOLO 版本和超参数固化成项目规格，再从项目触发单机训练任务。"
    />

    <section class="content-grid">
      <article class="panel">
        <p class="eyebrow">新增训练项目</p>
        <div class="form-grid">
          <input v-model.trim="form.name" class="input" placeholder="项目名称" />
          <select v-model="form.datasetId" class="input">
            <option value="">选择数据集</option>
            <option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id">{{ dataset.name }}</option>
          </select>
          <input v-model.trim="form.yoloVersion" class="input" placeholder="YOLOv11m" />
          <input v-model.number="form.imageSize" class="input" type="number" placeholder="输入尺寸" />
          <input v-model.number="form.epochs" class="input" type="number" placeholder="epoch" />
          <input v-model.number="form.batchSize" class="input" type="number" placeholder="batch" />
          <input v-model.trim="form.optimizer" class="input" placeholder="优化器" />
          <textarea v-model.trim="form.objective" class="input input-wide textarea" placeholder="本次实验目标，例如提高小目标召回" />
        </div>
        <div class="form-actions">
          <button class="button" :disabled="submitting" @click="createProject">
            {{ submitting ? '保存中...' : '创建项目' }}
          </button>
        </div>
      </article>

      <article class="panel table-panel">
        <p class="eyebrow">项目列表</p>
        <table v-if="items.length" class="table">
          <thead>
            <tr>
              <th>项目</th>
              <th>数据集</th>
              <th>规格</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td>
                <strong>{{ item.name }}</strong>
                <div class="table-subtle">{{ item.objective }}</div>
              </td>
              <td>{{ item.datasetName }}</td>
              <td>{{ item.yoloVersion }} · {{ item.imageSize }} · {{ item.epochs }}e · b{{ item.batchSize }}</td>
              <td><span class="chip">{{ item.status }}</span></td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无项目" text="先把数据集和实验目标整理成训练项目。" />
      </article>
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
const datasets = ref([])
const message = ref('')
const error = ref('')
const submitting = ref(false)
const form = ref({
  name: '',
  objective: '',
  datasetId: '',
  yoloVersion: 'YOLOv11m',
  imageSize: 640,
  epochs: 120,
  batchSize: 16,
  optimizer: 'SGD',
})

async function loadAll() {
  const [projectResponse, datasetResponse] = await Promise.all([
    request('/api/projects'),
    request('/api/datasets'),
  ])
  items.value = projectResponse.items
  datasets.value = datasetResponse.items
}

async function createProject() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    await request('/api/projects', {
      method: 'POST',
      body: JSON.stringify(form.value),
    })
    form.value = {
      name: '',
      objective: '',
      datasetId: '',
      yoloVersion: 'YOLOv11m',
      imageSize: 640,
      epochs: 120,
      batchSize: 16,
      optimizer: 'SGD',
    }
    await loadAll()
    message.value = '训练项目已创建。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadAll)
</script>
