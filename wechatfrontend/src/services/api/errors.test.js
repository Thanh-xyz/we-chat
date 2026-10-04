import { describe, expect, it } from 'vitest'
import { normalizeApiError } from './errors.js'

describe('API error normalization', () => {
  it('keeps safe backend messages and validation fields', () => {
    const error = normalizeApiError({ response: { status: 400, data: { message: 'Validation failed', validationErrors: { email: 'invalid' } } } })
    expect(error.status).toBe(400)
    expect(error.message).toBe('Validation failed')
    expect(error.validationErrors).toEqual({ email: 'invalid' })
  })

  it('provides a friendly rate limit message', () => {
    expect(normalizeApiError({ response: { status: 429 } }).message).toMatch(/quá nhanh/i)
  })

  it('identifies canceled requests', () => {
    expect(normalizeApiError({ code: 'ERR_CANCELED' }).code).toBe('REQUEST_CANCELED')
  })
})
