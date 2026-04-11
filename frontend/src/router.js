import { createRouter, createWebHistory } from 'vue-router'
import OverviewPage from './views/OverviewPage.vue'
import DatasetsPage from './views/DatasetsPage.vue'
import ProjectsPage from './views/ProjectsPage.vue'
import JobsPage from './views/JobsPage.vue'
import ModelsPage from './views/ModelsPage.vue'
import GemmaPage from './views/GemmaPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'overview', component: OverviewPage },
    { path: '/datasets', name: 'datasets', component: DatasetsPage },
    { path: '/projects', name: 'projects', component: ProjectsPage },
    { path: '/jobs', name: 'jobs', component: JobsPage },
    { path: '/models', name: 'models', component: ModelsPage },
    { path: '/gemma', name: 'gemma', component: GemmaPage },
  ],
})

export default router
