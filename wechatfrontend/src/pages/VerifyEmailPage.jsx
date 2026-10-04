import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthLayout, FormAlert } from '../components/auth/AuthLayout.jsx'
import { authApi } from '../services/api/authApi.js'
import { errorMessage } from '../services/api/errors.js'

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [status, setStatus] = useState(token ? 'loading' : 'error')
  const [message, setMessage] = useState(token ? '' : 'Liên kết xác minh không hợp lệ.')

  useEffect(() => {
    if (!token) return
    let active = true
    authApi.verifyEmail(token).then(() => {
      if (active) setStatus('success')
    }).catch((requestError) => {
      if (active) {
        setStatus('error')
        setMessage(errorMessage(requestError))
      }
    })
    return () => { active = false }
  }, [token])

  return (
    <AuthLayout eyebrow="Xác minh email" title={status === 'success' ? 'Email đã được xác minh' : 'Đang xác minh'} footer={<Link to="/login">Đi đến đăng nhập</Link>}>
      {status === 'loading' && <div className="inline-loading"><span className="spinner" /> Vui lòng chờ…</div>}
      {status === 'success' && <FormAlert tone="success">Tài khoản của bạn đã sẵn sàng.</FormAlert>}
      {status === 'error' && <FormAlert>{message}</FormAlert>}
    </AuthLayout>
  )
}
