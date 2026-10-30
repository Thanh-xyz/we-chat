import { useCallback, useEffect, useRef, useState } from 'react'
import { attachmentApi } from '../../services/api/attachmentApi.js'
import { errorMessage } from '../../services/api/errors.js'
import {
  MAX_ATTACHMENTS_PER_MESSAGE,
  validateAttachment,
} from '../../utils/attachments.js'

export function useAttachmentQueue(conversationId) {
  const [attachments, setAttachments] = useState([])
  const uploadControllers = useRef(new Map())

  const updateAttachment = useCallback((id, patch) => {
    setAttachments((current) => current.map((item) => (
      item.id === id ? { ...item, ...patch } : item
    )))
  }, [])

  const uploadAttachment = useCallback(async (item) => {
    const controller = new AbortController()
    uploadControllers.current.set(item.id, controller)
    updateAttachment(item.id, { status: 'uploading', progress: 0 })
    try {
      const result = await attachmentApi.upload(conversationId, item.file, item.fileType, {
        signal: controller.signal,
        onUploadProgress: (event) => updateAttachment(item.id, {
          progress: event.total ? Math.round((event.loaded / event.total) * 100) : 0,
        }),
      })
      updateAttachment(item.id, { status: 'success', progress: 100, result })
    } catch (requestError) {
      const cancelled = requestError.code === 'ERR_CANCELED' || requestError.code === 'REQUEST_CANCELED'
      updateAttachment(item.id, {
        status: cancelled ? 'cancelled' : 'failed',
        error: cancelled ? 'Đã hủy tải lên.' : errorMessage(requestError),
      })
    } finally {
      uploadControllers.current.delete(item.id)
    }
  }, [conversationId, updateAttachment])

  useEffect(() => () => {
    uploadControllers.current.forEach((controller) => controller.abort())
    uploadControllers.current.clear()
  }, [conversationId])

  const addFiles = useCallback((event) => {
    const files = [...event.target.files]
    event.target.value = ''
    if (!files.length) return

    const available = Math.max(0, MAX_ATTACHMENTS_PER_MESSAGE - attachments.length)
    let acceptedType = attachments.find((item) => item.status !== 'failed' && item.status !== 'cancelled' && item.status !== 'rejected')?.fileType
    const next = files.slice(0, available).map((file) => {
      const validation = validateAttachment(file)
      const mixedType = validation.valid && acceptedType && validation.fileType !== acceptedType
      if (validation.valid && !acceptedType) acceptedType = validation.fileType
      return {
        id: crypto.randomUUID(),
        file,
        fileType: validation.fileType,
        status: mixedType ? 'rejected' : validation.valid ? 'queued' : 'failed',
        progress: 0,
        error: mixedType ? 'Mỗi tin nhắn chỉ hỗ trợ một loại tệp.' : validation.valid ? '' : validation.message,
      }
    })
    setAttachments((current) => [...current, ...next])
    next.filter((item) => item.status === 'queued').forEach(uploadAttachment)
  }, [attachments, uploadAttachment])

  const removeAttachment = useCallback(async (item) => {
    uploadControllers.current.get(item.id)?.abort()
    if (item.result?.id) {
      try {
        await attachmentApi.remove(item.result.id)
      } catch {
        // Best-effort cleanup; removing it from the composer remains safe.
      }
    }
    setAttachments((current) => current.filter((candidate) => candidate.id !== item.id))
  }, [])

  const retryAttachment = useCallback((item) => {
    const acceptedType = attachments.find((candidate) => candidate.id !== item.id
      && candidate.status !== 'failed'
      && candidate.status !== 'cancelled'
      && candidate.status !== 'rejected')?.fileType
    if (acceptedType && item.fileType !== acceptedType) {
      updateAttachment(item.id, { status: 'rejected', error: 'Mỗi tin nhắn chỉ hỗ trợ một loại tệp.' })
      return
    }
    updateAttachment(item.id, { status: 'queued', error: '' })
    uploadAttachment(item)
  }, [attachments, updateAttachment, uploadAttachment])

  const clearAttachments = useCallback(() => setAttachments([]), [])

  const rejectedNames = attachments.filter((item) => item.status === 'rejected').map((item) => item.file.name)
  const validationError = rejectedNames.length
    ? `Không tải lên tệp khác loại: ${rejectedNames.join(', ')}`
    : ''

  return { attachments, validationError, addFiles, removeAttachment, retryAttachment, clearAttachments }
}
