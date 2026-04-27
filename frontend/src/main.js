import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles.css'

if (window.location.protocol === 'http:' && ['10.0.121.52', '14.23.49.218'].includes(window.location.hostname)) {
  window.location.replace(`https://${window.location.host}${window.location.pathname}${window.location.search}${window.location.hash}`)
}

createApp(App).use(router).mount('#app')
