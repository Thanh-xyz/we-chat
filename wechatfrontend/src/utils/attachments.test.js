import { describe, expect, it } from 'vitest'
import { attachmentTypeFor, formatFileSize, validateAttachment } from './attachments.js'

describe('attachment validation', () => {
  it('maps supported media to the backend message types', () => {
    expect(attachmentTypeFor(new File(['x'], 'photo.png', { type: 'image/png' }))).toBe('IMAGE')
    expect(attachmentTypeFor(new File(['x'], 'voice.mp3', { type: 'audio/mpeg' }))).toBe('VOICE')
    expect(attachmentTypeFor(new File(['x'], 'note.txt', { type: 'text/plain' }))).toBe('FILE')
  })

  it('rejects unsupported and oversized files before upload', () => {
    expect(validateAttachment(new File(['x'], 'malware.exe', { type: 'application/x-msdownload' })).valid).toBe(false)
    const large = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' })
    expect(validateAttachment(large).valid).toBe(false)
  })

  it('formats upload sizes for the queue', () => {
    expect(formatFileSize(1024)).toBe('1 KB')
    expect(formatFileSize(1024 * 1024)).toBe('1.0 MB')
  })
})
