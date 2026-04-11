<template>
  <section class="page">
    <PageHeader
      eyebrow="Models"
      title="模型仓库"
      description="集中登记本机导出的 YOLO 模型产物，包括 best.pt、ONNX 或 TensorRT 等推理格式。"
    />

    <section class="content-grid">
      <article class="panel">
        <p class="eyebrow">手动登记模型</p>
        <div class="form-grid">
          <input v-model.trim="form.name" class="input" placeholder="模型名称" />
          <input v-model.trim="form.projectName" class="input" placeholder="所属项目" />
          <input v-model.trim="form.yoloVersion" class="input" placeholder="YOLO 版本" />
          <input v-model.trim="form.exportFormat" class="input" placeholder="导出格式，例如 onnx" />
          <input v-model.number="form.map50" class="input" type="number" step="0.001" placeholder="mAP50" />
          <input v-model.trim="form.filePath" class="input input-wide" placeholder="本地文件路径" />
        </div>
        <div class="form-actions">
          <button class="button" :disabled="submitting" @click="registerModel">
            {{ submitting ? '登记中...' : '登记模型' }}
          </button>
        </div>
      </article>

      <article class="panel table-panel">
        <p class="eyebrow">模型列表</p>
        <table v-if="items.length" class="table">
          <thead>
            <tr>
              <th>模型</th>
              <th>项目</th>
              <th>版本</th>
              <th>指标</th>
              <th>路径</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td>
                <strong>{{ item.name }}</strong>
                <div class="table-subtle">{{ item.exportFormat }} · {{ item.status }}</div>
              </td>
              <td>{{ item.projectName }}</td>
              <td>{{ item.yoloVersion }}</td>
              <td>mAP50 {{ item.map50 }}</td>
              <td>{{ item.filePath }}</td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无模型" text="训练完成后或手动导入后，这里会显示模型产物。" />
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
const form = ref({
  name: '',
  projectName: '',
  yoloVersion: 'YOLOv11m',
  exportFormat: 'onnx',
  map50: 0.0,
  filePath: '',
})

async function loadModels() {
  const response = await request('/api/models')
  items.value = response.items
}

async function registerModel() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    await request('/api/models', {
      method: 'POST',
      body: JSON.stringify(form.value),
    })
    form.value = {
      name: '',
      projectName: '',
      yoloVersion: 'YOLOv11m',
      exportFormat: 'onnx',
      map50: 0.0,
      filePath: '',
    }
    await loadModels()
    message.value = '模型已登记。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadModels)
</script>
