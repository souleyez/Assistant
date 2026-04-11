<template>
  <section class="page">
    <PageHeader
      eyebrow="Single Machine"
      title="Gemma 4 + YOLO 训练平台"
      description="纯单机版工作流：本地数据集、本地训练任务、本地模型产物，以及 Gemma 4 辅助策略建议。"
    />

    <div v-if="loading" class="panel">加载中...</div>
    <div v-else-if="error" class="panel error-panel">{{ error }}</div>
    <template v-else-if="data">
      <section class="stats-grid">
        <StatCard label="数据集" :value="data.metrics.datasetCount" subtle="local datasets" />
        <StatCard label="项目" :value="data.metrics.projectCount" subtle="training specs" />
        <StatCard label="运行中任务" :value="data.metrics.runningJobs" subtle="gpu active jobs" />
        <StatCard label="模型产物" :value="data.metrics.modelCount" subtle="registered artifacts" />
      </section>

      <section class="dashboard-grid">
        <article class="panel hero-panel">
          <p class="eyebrow">Machine Profile</p>
          <h2>{{ data.platform.machine.hostName }}</h2>
          <p>
            {{ data.platform.machine.gpu }} · {{ data.platform.machine.memoryGb }}GB RAM ·
            {{ data.platform.machine.workspacePath }}
          </p>
          <div class="chip-row">
            <span class="chip">{{ data.platform.machine.mode }}</span>
            <span class="chip">{{ data.platform.machine.storagePolicy }}</span>
            <span class="chip">{{ data.platform.runtime.yoloEngine }}</span>
          </div>
        </article>

        <article class="panel">
          <p class="eyebrow">Runtime</p>
          <div class="list-stack">
            <div class="list-row">
              <div>
                <strong>Gemma 4 Advisor</strong>
                <p>{{ data.platform.runtime.gemmaModel }}</p>
              </div>
              <span class="chip">{{ data.platform.runtime.gemmaReady ? 'ready' : 'planned' }}</span>
            </div>
            <div class="list-row">
              <div>
                <strong>YOLO Engine</strong>
                <p>{{ data.platform.runtime.pythonEnv }} · {{ data.platform.runtime.pythonCommand }}</p>
              </div>
              <span class="chip">{{ data.platform.runtime.yoloReady ? 'ready' : 'blocked' }}</span>
            </div>
            <div class="list-row">
              <div>
                <strong>Training Occupancy</strong>
                <p>是否占用单机训练通道</p>
              </div>
              <span class="chip">{{ data.platform.runtime.trainingBusy ? 'busy' : 'idle' }}</span>
            </div>
            <div class="list-row">
              <div>
                <strong>Workspace</strong>
                <p>{{ data.platform.runtime.trainingWorkspace }}</p>
              </div>
              <span class="chip">{{ data.platform.runtime.defaultBaseModel }}</span>
            </div>
          </div>
        </article>

        <article class="panel">
          <p class="eyebrow">Recent Jobs</p>
          <div class="list-stack">
            <div v-for="job in data.latestJobs.slice(0, 3)" :key="job.id" class="list-row">
              <div>
                <strong>{{ job.projectName }}</strong>
                <p>{{ job.datasetName }} · epoch {{ job.currentEpoch }}/{{ job.totalEpochs }}</p>
              </div>
              <span class="chip">{{ job.status }}</span>
            </div>
          </div>
        </article>

        <article class="panel">
          <p class="eyebrow">Latest Models</p>
          <div class="list-stack">
            <div v-for="model in data.latestModels.slice(0, 3)" :key="model.id" class="list-row">
              <div>
                <strong>{{ model.name }}</strong>
                <p>{{ model.projectName }} · {{ model.exportFormat }}</p>
              </div>
              <span class="chip">mAP50 {{ model.map50 }}</span>
            </div>
          </div>
        </article>
      </section>

      <section class="panel">
        <p class="eyebrow">Recent Activity</p>
        <div class="timeline">
          <div v-for="item in data.timeline" :key="item.id" class="timeline-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
            <small>{{ formatTime(item.at) }}</small>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup>
import { onMounted } from 'vue'
import { request } from '../api'
import { useAsyncState } from '../composables/useAsyncState'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'

const { data, loading, error, run } = useAsyncState(null)

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

onMounted(() => {
  run(() => request('/api/overview'))
})
</script>
