import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: {
          title: '首页',
          icon: 'HomeFilled',
          breadcrumb: [{ name: '首页' }]
        }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        redirect: '/knowledge/list',
        meta: {
          title: '知识管理',
          icon: 'Reading',
          breadcrumb: [{ name: '知识管理' }]
        },
        children: [
          {
            path: 'list',
            name: 'KnowledgeList',
            component: () => import('@/views/knowledge/List.vue'),
            meta: {
              title: '知识列表',
              icon: 'List',
              breadcrumb: [
                { name: '知识管理', path: '/knowledge' },
                { name: '知识列表' }
              ]
            }
          }
        ]
      },
      {
        path: 'ai',
        name: 'Ai',
        redirect: '/ai/send',
        meta: {
          title: 'AI 助手',
          icon: 'Cpu',
          breadcrumb: [{ name: 'AI 助手' }]
        },
        children: [
          {
            path: 'send',
            name: 'AiSend',
            component: () => import('@/views/ai/SendChat.vue'),
            meta: {
              title: '单轮对话',
              icon: 'ChatDotRound',
              breadcrumb: [
                { name: 'AI 助手', path: '/ai' },
                { name: '单轮对话' }
              ]
            }
          },
          {
            path: 'cache',
            name: 'AiCache',
            component: () => import('@/views/ai/CacheChat.vue'),
            meta: {
              title: '缓存问答',
              icon: 'Coin',
              breadcrumb: [
                { name: 'AI 助手', path: '/ai' },
                { name: '缓存问答' }
              ]
            }
          },
          {
            path: 'concurrency',
            name: 'AiConcurrency',
            component: () => import('@/views/ai/ConcurrencyChat.vue'),
            meta: {
              title: '并发控制',
              icon: 'Connection',
              breadcrumb: [
                { name: 'AI 助手', path: '/ai' },
                { name: '并发控制' }
              ]
            }
          },
          {
            path: 'stream',
            name: 'AiStream',
            component: () => import('@/views/ai/StreamChat.vue'),
            meta: {
              title: '流式对话',
              icon: 'Promotion',
              breadcrumb: [
                { name: 'AI 助手', path: '/ai' },
                { name: '流式对话' }
              ]
            }
          }
        ]
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { hidden: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router