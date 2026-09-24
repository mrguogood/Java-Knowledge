import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const pathToComponentName = {
  '/home': 'Home',
  '/knowledge/list': 'KnowledgeList',
  '/ai/send': 'SendChat',
  '/ai/cache': 'CacheChat',
  '/ai/concurrency': 'ConcurrencyChat',
  '/ai/stream': 'StreamChat'
}

export const useAppStore = defineStore('app', () => {
  const collapsed = ref(false)

  const toggleCollapse = () => {
    collapsed.value = !collapsed.value
  }

  const breadcrumbs = ref([{ name: '首页' }])

  const setBreadcrumbs = (list) => {
    breadcrumbs.value = list
  }

  const menuList = ref([
    {
      path: '/home',
      name: '首页',
      icon: 'HomeFilled',
      children: []
    },
    {
      path: '/knowledge',
      name: '知识管理',
      icon: 'Reading',
      children: [
        {
          path: '/knowledge/list',
          name: '知识列表',
          icon: 'List'
        }
      ]
    },
    {
      path: '/ai',
      name: 'AI 助手',
      icon: 'Cpu',
      children: [
        {
          path: '/ai/send',
          name: '单轮对话',
          icon: 'ChatDotRound'
        },
        {
          path: '/ai/cache',
          name: '缓存问答',
          icon: 'Coin'
        },
        {
          path: '/ai/concurrency',
          name: '并发控制',
          icon: 'Connection'
        },
        {
          path: '/ai/stream',
          name: '流式对话',
          icon: 'Promotion'
        }
      ]
    }
  ])

  // ---------- 标签页 ----------
  const tabs = ref([
    { path: '/home', title: '首页', closable: false }
  ])

  const cachedNames = computed(() => {
    return tabs.value
      .map(t => pathToComponentName[t.path])
      .filter(Boolean)
  })

  const addTab = (tab) => {
    const exists = tabs.value.some(t => t.path === tab.path)
    if (!exists) {
      tabs.value.push({
        path: tab.path,
        title: tab.title,
        closable: tab.path !== '/home'
      })
    }
  }

  const removeTab = (path) => {
    const index = tabs.value.findIndex(t => t.path === path)
    if (index === -1) return null
    if (!tabs.value[index].closable) return null
    tabs.value.splice(index, 1)
    if (tabs.value.length > 0) {
      const nextTab = tabs.value[Math.min(index, tabs.value.length - 1)]
      return nextTab.path
    }
    return '/home'
  }

  return {
    collapsed,
    toggleCollapse,
    breadcrumbs,
    setBreadcrumbs,
    menuList,
    tabs,
    cachedNames,
    addTab,
    removeTab
  }
})