import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../services/websocket/WebSocketManager.js', () => ({ webSocketManager: { connect: vi.fn(), disconnect: vi.fn() } }))

import { applyPresenceEvent } from './realtimeStore.js'
import { useChatStore } from '../conversations/chatStore.js'
import { useContactStore } from '../contacts/contactStore.js'
import { usePeopleStore } from '../people/peopleStore.js'
import { directPeerStatus } from '../../utils/chat.js'

describe('presence.updated realtime events', () => {
  beforeEach(() => {
    useChatStore.getState().reset()
    useContactStore.getState().reset()
    usePeopleStore.getState().reset()
  })

  it('updates people, direct conversation peers, and contacts', () => {
    usePeopleStore.getState().remember([{ userId: 'u2', username: 'an', status: 'OFFLINE' }])
    const direct = { id: 'c1', type: 'DIRECT', memberIds: ['u1', 'u2'], directPeer: { userId: 'u2', presenceStatus: 'OFFLINE' } }
    useChatStore.setState({ conversations: [direct], conversationsById: { c1: direct } })
    useContactStore.setState({ friends: [{ userId: 'u2', status: 'OFFLINE' }] })

    applyPresenceEvent({ type: 'presence.updated', actorUserId: 'u2', payload: { status: 'ONLINE' } })

    expect(usePeopleStore.getState().byId.u2.status).toBe('ONLINE')
    expect(useChatStore.getState().conversations[0].directPeer.presenceStatus).toBe('ONLINE')
    expect(useChatStore.getState().conversationsById.c1.directPeer.presenceStatus).toBe('ONLINE')
    expect(useContactStore.getState().friends[0].status).toBe('ONLINE')
  })

  it('resolves direct peer status from the people cache before the server snapshot', () => {
    const conversation = { type: 'DIRECT', memberIds: ['u1', 'u2'], directPeer: { userId: 'u2', presenceStatus: 'OFFLINE' } }
    expect(directPeerStatus(conversation, { currentUserId: 'u1', people: { u2: { status: 'ONLINE' } } })).toBe('ONLINE')
    expect(directPeerStatus(conversation, { currentUserId: 'u1', people: {} })).toBe('OFFLINE')
    expect(directPeerStatus({ type: 'GROUP', memberIds: [] }, {})).toBeNull()
  })
})
