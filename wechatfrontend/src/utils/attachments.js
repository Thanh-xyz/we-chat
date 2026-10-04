export const ATTACHMENT_LIMITS = {
  IMAGE: 10 * 1024 * 1024,
  FILE: 50 * 1024 * 1024,
  VOICE: 25 * 1024 * 1024,
}

const MIME_TYPES = {
  IMAGE: new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']),
  VOICE: new Set(['audio/mpeg', 'audio/wav', 'audio/x-wav', 'audio/mp4', 'audio/webm', 'audio/x-m4a']),
  FILE: new Set([
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'text/plain',
    'application/zip',
    'application/x-zip-compressed',
  ]),
}

export function attachmentTypeFor(file) {
  if (file.type.startsWith('image/')) return 'IMAGE'
  if (file.type.startsWith('audio/')) return 'VOICE'
  return 'FILE'
}

export function validateAttachment(file) {
  const fileType = attachmentTypeFor(file)
  if (!MIME_TYPES[fileType].has(file.type)) {
    return { valid: false, fileType, message: 'Định dạng tệp chưa được hỗ trợ.' }
  }
  if (file.size <= 0) {
    return { valid: false, fileType, message: 'Tệp không có nội dung.' }
  }
  if (file.size > ATTACHMENT_LIMITS[fileType]) {
    return { valid: false, fileType, message: `Tệp ${fileType === 'IMAGE' ? 'ảnh' : fileType === 'VOICE' ? 'thoại' : ''} vượt quá giới hạn cho phép.` }
  }
  return { valid: true, fileType, message: '' }
}

export function formatFileSize(bytes = 0) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
