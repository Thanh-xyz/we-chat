import axios from 'axios'
import { apiClient } from './client.js'

export const attachmentApi = {
  upload: async (conversationId, file, fileType, { signal, onUploadProgress } = {}) => {
    const { data: uploadRequest } = await apiClient.post('/attachments/presigned-upload', {
      conversationId,
      originalFileName: file.name,
      fileType,
      mimeType: file.type,
      fileSize: file.size,
    }, {
      signal,
      timeout: 15_000,
    })
    await axios.put(uploadRequest.uploadUrl, file, {
      signal,
      headers: uploadRequest.headers,
      onUploadProgress,
      timeout: 120_000,
    })
    const { data } = await apiClient.post('/attachments/complete', {
      attachmentId: uploadRequest.attachmentId,
      originalFileName: file.name,
      mimeType: file.type,
      fileType,
      fileSize: file.size,
    }, { signal, timeout: 30_000 })
    return data
  },
  download: async (fileUrl, { signal } = {}) => {
    const path = fileUrl.startsWith('/api') ? fileUrl.slice(4) : fileUrl
    return apiClient.get(path, { signal, responseType: 'blob', timeout: 120_000 })
  },
  downloadById: async (attachmentId, { signal } = {}) => {
    const { data } = await apiClient.get(`/attachments/${attachmentId}/download-url`, { signal, timeout: 15_000 })
    return axios.get(data.downloadUrl, { signal, responseType: 'blob', timeout: 120_000 })
  },
  remove: async (attachmentId) => {
    await apiClient.delete(`/attachments/${attachmentId}`)
  },
}
