<template>
  <section class="page">
    <PageHeader
      eyebrow="Models"
      title="模型仓库"
      description="集中登记本机导出的 YOLO 模型产物，并展示默认算法包转换结果。训练产物会优先自动打包，手工或历史 PT/ONNX 模型也可填写芯片后转成 RKNN。"
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
          <input v-model.trim="form.filePath" class="input input-wide" placeholder="本地文件路径（pt / onnx / rknn）" />
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
              <th>RKNN</th>
              <th>算法包</th>
              <th>路径</th>
              <th>操作</th>
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
              <td>
                <div class="table-subtle">{{ item.targetChip || '未指定芯片' }} · {{ item.rknnStatus || 'n/a' }}</div>
                <div class="table-subtle">{{ item.rknnMessage || '训练完成后可由操作员填写芯片并执行转换。' }}</div>
              </td>
              <td>
                <div class="table-subtle">{{ item.packageVariant || 'm1' }} · {{ item.packageStatus || 'n/a' }}</div>
                <div class="table-subtle">{{ item.packageMessage || '训练完成后会尝试自动打包。' }}</div>
              </td>
              <td>
                <div class="table-subtle">model {{ item.filePath }}</div>
                <div class="table-subtle">rknn {{ item.rknnPath || '-' }}</div>
                <div class="table-subtle">package {{ item.packageArchivePath || item.packageDir || '-' }}</div>
              </td>
              <td>
                <div class="inline-actions">
                  <input
                    v-model.trim="targetChips[item.id]"
                    class="input"
                    placeholder="rk3588 / rk3568 / rv1106"
                    :disabled="!canConvert(item)"
                  />
                  <button
                    class="button button-secondary"
                    :disabled="submitting || !targetChips[item.id] || !canConvert(item)"
                    @click="convertToRknn(item.id)"
                  >
                    转 RKNN
                  </button>
                </div>
              </td>
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
const targetChips = ref({})
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
  const next = {}
  for (const item of items.value) {
    next[item.id] = targetChips.value[item.id] || item.targetChip || ''
  }
  targetChips.value = next
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

async function convertToRknn(id) {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    await request(`/api/models/${id}/convert-rknn`, {
      method: 'POST',
      body: JSON.stringify({ targetChip: targetChips.value[id] }),
    })
    await loadModels()
    message.value = 'RKNN 转换已提交；若模型带训练元数据，会继续生成默认算法包。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'RKNN 转换失败'
  } finally {
    submitting.value = false
  }
}

function canConvert(item) {
  const format = String(item?.sourceModelFormat || item?.exportFormat || '').toLowerCase()
  return ['pt', 'onnx', 'rknn'].includes(format) && item?.rknnStatus !== 'running'
}

onMounted(loadModels)
</script>
