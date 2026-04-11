<template>
  <section class="page">
    <PageHeader
      eyebrow="Datasets"
      title="YOLO 数据集"
      description="登记本机数据集目录、样本规模、类别数量和版本信息，训练平台只关心本地单机工作区。"
    />

    <section class="content-grid">
      <article class="panel">
        <p class="eyebrow">新增数据集</p>
        <div class="form-grid">
          <input v-model.trim="form.name" class="input" placeholder="数据集名称" />
          <input v-model.trim="form.storagePath" class="input input-wide" placeholder="本地路径，例如 D:/datasets/helmet-v4" />
          <input v-model.number="form.imageCount" class="input" type="number" placeholder="图片数量" />
          <input v-model.number="form.classCount" class="input" type="number" placeholder="类别数" />
          <input v-model.number="form.version" class="input" type="number" placeholder="版本" />
          <select v-model="form.status" class="input">
            <option value="ready">ready</option>
            <option value="reviewing">reviewing</option>
            <option value="draft">draft</option>
          </select>
          <input v-model.trim="classNamesText" class="input input-wide" placeholder="类别名，逗号分隔，例如 helmet,person,vest" />
          <textarea v-model.trim="form.notes" class="input input-wide textarea" placeholder="补充备注，例如弱光样本、标注质量、Gemma 建议" />
        </div>
        <div class="form-actions">
          <button class="button" :disabled="submitting" @click="createDataset">
            {{ submitting ? '保存中...' : '登记数据集' }}
          </button>
        </div>
      </article>

      <article class="panel table-panel">
        <p class="eyebrow">数据集列表</p>
        <table v-if="items.length" class="table">
          <thead>
            <tr>
              <th>名称</th>
              <th>路径</th>
              <th>样本</th>
              <th>类别</th>
              <th>类别名</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td>
                <strong>{{ item.name }}</strong>
                <div class="table-subtle">v{{ item.version }} · {{ item.labelFormat }}</div>
              </td>
              <td>{{ item.storagePath }}</td>
              <td>{{ item.imageCount }}</td>
              <td>{{ item.classCount }}</td>
              <td>{{ (item.classNames || []).join(', ') }}</td>
              <td><span class="chip">{{ item.status }}</span></td>
              <td><button class="button button-danger" @click="deleteDataset(item.id)">删除</button></td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无数据集" text="先登记本机 YOLO 数据集目录。" />
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
const message = ref('')
const error = ref('')
const submitting = ref(false)
const classNamesText = ref('')
const form = ref({
  name: '',
  storagePath: '',
  imageCount: 0,
  classCount: 1,
  version: 1,
  status: 'ready',
  notes: '',
})

async function loadDatasets() {
  const response = await request('/api/datasets')
  items.value = response.items
}

async function createDataset() {
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    await request('/api/datasets', {
      method: 'POST',
      body: JSON.stringify({
        ...form.value,
        classNames: classNamesText.value
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean),
      }),
    })
    classNamesText.value = ''
    form.value = { name: '', storagePath: '', imageCount: 0, classCount: 1, version: 1, status: 'ready', notes: '' }
    await loadDatasets()
    message.value = '数据集已登记。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

async function deleteDataset(id) {
  await request(`/api/datasets/${id}`, { method: 'DELETE' })
  await loadDatasets()
  message.value = '数据集已删除。'
}

onMounted(loadDatasets)
</script>
