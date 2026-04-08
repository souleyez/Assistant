import { createRouter, createWebHistory } from 'vue-router'
import DashboardPage from './views/DashboardPage.vue'
import DocumentsPage from './views/DocumentsPage.vue'
import DatasourcesPage from './views/DatasourcesPage.vue'
import ReportsPage from './views/ReportsPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: DashboardPage },
    { path: '/documents', name: 'documents', component: DocumentsPage },
    { path: '/datasources', name: 'datasources', component: DatasourcesPage },
    { path: '/reports', name: 'reports', component: ReportsPage },
  ],
})

export default router
