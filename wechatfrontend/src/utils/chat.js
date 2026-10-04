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

export function conversationName(conversation) {
  if (!conversation) return 'Cuộc trò chuyện'
  if (conversation.name?.trim()) return conversation.name.trim()
  return conversation.type === 'DIRECT' ? 'Trò chuyện trực tiếp' : 'Nhóm chưa đặt tên'
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
