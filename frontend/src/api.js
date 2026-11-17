import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message))
      }
      return body.data
    }
    return body
  },
  (err) => {
    ElMessage.error(err?.response?.data?.message || err.message || '网络异常')
    return Promise.reject(err)
  }
)

export const api = {
  listTasks: () => http.get('/tasks'),
  createTask: (data) => http.post('/tasks', data),
  updateStatus: (id, status) => http.put(`/tasks/${id}/status`, { status }),
  runNow: (id) => http.post(`/tasks/${id}/run`),
  executions: (id, params) => http.get(`/tasks/${id}/executions`, { params }),
  updateShards: (id, shardTotal) => http.put(`/tasks/${id}/shards`, { shardTotal }),
  updateAlert: (id, data) => http.put(`/tasks/${id}/alert`, data),
  getAlert: (id) => http.get(`/tasks/${id}/alert`),
  executionDetail: (id) => http.get(`/executions/${id}`),
  logs: (id, params) => http.get(`/executions/${id}/logs`, { params }),
  shards: (id) => http.get(`/executions/${id}/shards`),
  retry: (id) => http.post(`/executions/${id}/retry`),
  compensate: (id) => http.post(`/executions/${id}/compensate`),
  nodes: () => http.get('/nodes'),
  alerts: (params) => http.get('/alerts', { params })
}

export default api
