import { apiClient } from './client.js'

export const attachmentApi = {
  upload: async (conversationId, file, fileType, { signal, onUploadProgress } = {}) => {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post('/attachments/upload', formData, {
      signal,
      params: { conversationId, fileType },
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress,
      timeout: 120_000,
    })
    return data
  },
  download: async (fileUrl, { signal } = {}) => {
    const path = fileUrl.startsWith('/api') ? fileUrl.slice(4) : fileUrl
    return apiClient.get(path, { signal, responseType: 'blob', timeout: 120_000 })
  },
  remove: async (attachmentId) => {
    await apiClient.delete(`/attachments/${attachmentId}`)
  },
}
