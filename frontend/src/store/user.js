import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 用户信息 Store
 * 当前为 Mock 数据
 * TODO: 替换为真实 API —— 调用 /api/user/info 获取用户信息
 */
export const useUserStore = defineStore('user', () => {
  // ---------- Mock 用户信息 ----------
  const userInfo = ref({
    id: 1,
    username: 'admin',
    nickname: '管理员',
    avatar: '', // 空则使用默认头像
    role: 'admin',
    email: 'admin@company.com'
  })

  // ---------- 消息通知（Mock）----------
  const notifications = ref([
    { id: 1, title: '系统通知：版本 v2.0 已发布', read: false, time: '2026-09-22 10:30' },
    { id: 2, title: '知识库新增 5 条记录', read: false, time: '2026-09-21 14:00' },
    { id: 3, title: '欢迎使用企业知识库管理平台', read: true, time: '2026-09-20 09:00' }
  ])

  // TODO: 替换为真实 API
  // const fetchUserInfo = async () => {
  //   const res = await axios.get('/api/user/info')
  //   userInfo.value = res.data
  // }

  // TODO: 退出登录
  // const logout = async () => {
  //   await axios.post('/api/user/logout')
  //   router.push('/login')
  // }

  return {
    userInfo,
    notifications
  }
})