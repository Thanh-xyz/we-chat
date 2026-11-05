export function mergeMessages(current = [], incoming = []) {
  const byId = new Map(current.map((message) => [message.id, message]))
  incoming.forEach((message) => byId.set(message.id, { ...byId.get(message.id), ...message }))
  return [...byId.values()].sort((left, right) => {
    const timeDifference = new Date(left.createdAt) - new Date(right.createdAt)
    return timeDifference || left.id.localeCompare(right.id)
  })
}

export function updateConversationUnread(conversations, conversationId, unreadCount) {
  return conversations.map((conversation) =>
    conversation.id === conversationId ? { ...conversation, unreadCount } : conversation,
  )
}

export function sortConversations(conversations) {
  return [...conversations].sort((left, right) => {
    if (left.pinnedAt && !right.pinnedAt) return -1
    if (!left.pinnedAt && right.pinnedAt) return 1
    const leftDate = left.lastMessageAt || left.updatedAt || left.createdAt
    const rightDate = right.lastMessageAt || right.updatedAt || right.createdAt
    return new Date(rightDate) - new Date(leftDate)
  })
}

function personLabel(person) {
  return person?.displayName || person?.username || ''
}

export function otherMemberIds(conversation, currentUserId) {
  return (conversation?.memberIds || []).filter((id) => id !== currentUserId)
}

export function conversationName(conversation, { currentUserId = null, people = {} } = {}) {
  if (!conversation) return 'Cuộc trò chuyện'
  if (conversation.displayName?.trim()) return conversation.displayName.trim()
  if (conversation.name?.trim()) return conversation.name.trim()
  const others = otherMemberIds(conversation, currentUserId)
  if (conversation.type === 'DIRECT') {
    return personLabel(people[others[0]]) || 'Trò chuyện trực tiếp'
  }
  const names = others.map((id) => personLabel(people[id])).filter(Boolean)
  if (!names.length) return 'Nhóm chưa đặt tên'
  const shown = names.slice(0, 3).join(', ')
  return others.length > 3 ? `${shown} +${others.length - 3}` : shown
}

export function conversationAvatar(conversation, { currentUserId = null, people = {} } = {}) {
  if (!conversation) return null
  if (conversation.displayAvatarUrl) return conversation.displayAvatarUrl
  if (conversation.avatarUrl) return conversation.avatarUrl
  if (conversation.type !== 'DIRECT') return null
  return people[otherMemberIds(conversation, currentUserId)[0]]?.avatarUrl || null
}

const TYPE_PREVIEW = { IMAGE: '[Hình ảnh]', FILE: '[Tệp đính kèm]', VOICE: '[Tin nhắn thoại]' }

export function messagePreview(message, { currentUserId = null, people = {}, group = false } = {}) {
  if (!message) return ''
  if (message.recalled) return 'Tin nhắn đã được thu hồi'
  const body = message.content?.trim() || TYPE_PREVIEW[message.messageType] || ''
  if (message.messageType === 'SYSTEM') return body
  if (message.senderId === currentUserId) return `Bạn: ${body}`
  if (group) {
    const name = personLabel(people[message.senderId] || message.sender)
    if (name) return `${name.split(' ').at(-1)}: ${body}`
  }
  return body
}

export function isSameDay(left, right) {
  if (!left || !right) return false
  return new Date(left).toDateString() === new Date(right).toDateString()
}

export function formatDayLabel(value) {
  if (!value) return ''
  const date = new Date(value)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return 'Hôm nay'
  if (date.toDateString() === yesterday.toDateString()) return 'Hôm qua'
  const label = new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long', day: '2-digit', month: '2-digit',
    ...(date.getFullYear() !== today.getFullYear() ? { year: 'numeric' } : {}),
  }).format(date)
  return label.charAt(0).toUpperCase() + label.slice(1)
}

export function conversationPreview(conversation, latestMessage) {
  if (latestMessage) return latestMessage.recalled ? 'Tin nhắn đã được thu hồi' : latestMessage.content || attachmentPreview(latestMessage.messageType)
  const lastMessage = conversation?.lastMessage
  if (!lastMessage) return 'Bắt đầu cuộc trò chuyện'
  if (lastMessage.recalled) return 'Tin nhắn đã được thu hồi'
  const preview = lastMessage.contentPreview || attachmentPreview(lastMessage.messageType)
  return lastMessage.sentByCurrentUser && preview ? `Bạn: ${preview}` : preview
}

function attachmentPreview(messageType) {
  if (messageType === 'IMAGE') return '[Hình ảnh]'
  if (messageType === 'FILE') return '[Tệp đính kèm]'
  if (messageType === 'VOICE') return '[Tin nhắn thoại]'
  return ''
}

export function initials(value = '') {
  const parts = value.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return 'C'
  return parts.slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

export function formatMessageTime(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

export function formatConversationTime(value) {
  if (!value) return ''
  const date = new Date(value)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) return formatMessageTime(value)
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(date)
}

export function messageDateKey(value) {
  const date = new Date(value)
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

export function formatMessageDate(value) {
  const date = new Date(value)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return 'Hôm nay'
  if (date.toDateString() === yesterday.toDateString()) return 'Hôm qua'
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date)
}

export function presenceLabel(status) {
  return {
    ONLINE: 'Đang hoạt động',
    OFFLINE: 'Ngoại tuyến',
    AWAY: 'Vắng mặt',
    BUSY: 'Đang bận',
  }[status] || status || ''
}
