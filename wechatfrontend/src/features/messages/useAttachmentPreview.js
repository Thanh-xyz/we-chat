import { useCallback, useEffect, useState } from 'react'
import { attachmentApi } from '../../services/api/attachmentApi.js'

export function useAttachmentPreview(attachment, fileType, fileName) {
  const [url, setUrl] = useState('')

  useEffect(() => {
    if (fileType === 'FILE') return undefined
    let active = true
    let objectUrl = ''
    const controller = new AbortController()
    attachmentApi.downloadById(attachment.id, { signal: controller.signal }).then(({ data }) => {
      if (!active) return
      objectUrl = URL.createObjectURL(data)
      setUrl(objectUrl)
    }).catch(() => {})

    return () => {
      active = false
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [attachment.id, fileType])

  const download = useCallback(async () => {
    try {
      const { data } = await attachmentApi.downloadById(attachment.id)
      const objectUrl = URL.createObjectURL(data)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = fileName
      link.click()
      URL.revokeObjectURL(objectUrl)
    } catch {
      // The message remains visible; the next authenticated refresh can retry.
    }
  }, [attachment.id, fileName])

  return { url, download }
}
