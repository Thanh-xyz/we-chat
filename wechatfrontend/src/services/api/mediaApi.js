import axios from 'axios'
import { apiClient } from './client.js'

const CATEGORY_PATHS = { IMAGE: 'images', FILE: 'files', VOICE: 'voices' }

export const mediaApi = {
  list: async (conversationId, { category = null, limit = 30, offset = 0, signal } = {}) => {
    const suffix = category && CATEGORY_PATHS[category] ? `/${CATEGORY_PATHS[category]}` : ''
    return (await apiClient.get(`/conversations/${conversationId}/media${suffix}`, { signal, params: { limit, offset } })).data
  },
  search: async (conversationId, { fileName, category, limit = 30, offset = 0, signal } = {}) =>
    (await apiClient.get(`/conversations/${conversationId}/media/search`, {
      signal,
      params: { fileName: fileName || undefined, category: category || undefined, limit, offset },
    })).data,
  stats: async (conversationId, { signal } = {}) =>
    (await apiClient.get(`/conversations/${conversationId}/media/stats`, { signal })).data,
  detail: async (attachmentId, { signal } = {}) => (await apiClient.get(`/media/${attachmentId}`, { signal })).data,
  downloadBlob: async (attachmentId, { signal } = {}) => {
    const { data } = await apiClient.get(`/media/${attachmentId}/download-url`, { signal, timeout: 15_000 })
    return (await axios.get(data.downloadUrl, { signal, responseType: 'blob', timeout: 120_000 })).data
  },
  remove: async (attachmentId) => { await apiClient.delete(`/media/${attachmentId}`) },
}
