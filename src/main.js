import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'

// === 版本升级时自动清 SW 缓存，避免旧页面覆盖新 APK ===
const APP_VERSION = __BUILD_TIME__ // vite.config.js define，每次构建自动更新
const VERSION_KEY = 'ett_app_version'

async function handleVersionUpgrade() {
  const stored = localStorage.getItem(VERSION_KEY)
  if (stored === APP_VERSION) return // 同版本，无需处理

  // 新版本或首次安装：清理所有 Service Worker 缓存
  if ('caches' in window) {
    const keys = await caches.keys()
    await Promise.all(keys.map(k => caches.delete(k)))
  }
  // 注销旧 SW，下次加载注册新的
  if ('serviceWorker' in navigator) {
    const regs = await navigator.serviceWorker.getRegistrations()
    await Promise.all(regs.map(r => r.unregister()))
  }
  // 记录当前版本
  localStorage.setItem(VERSION_KEY, APP_VERSION)
}

handleVersionUpgrade().finally(() => {
  const app = createApp(App)
  app.use(ElementPlus)
  app.mount('#app')
})
