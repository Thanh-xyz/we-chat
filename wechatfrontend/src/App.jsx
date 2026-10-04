import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute, PublicOnlyRoute } from './app/RouteGuards.jsx'
import { useAuthStore } from './features/auth/authStore.js'
import { ChatPage } from './pages/ChatPage.jsx'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage.jsx'
import { LoginPage } from './pages/LoginPage.jsx'
import { RegisterPage } from './pages/RegisterPage.jsx'
import { ResetPasswordPage } from './pages/ResetPasswordPage.jsx'
import { VerifyEmailPage } from './pages/VerifyEmailPage.jsx'

export default function App() {
  const initialize = useAuthStore((state) => state.initialize)
  const expireSession = useAuthStore((state) => state.expireSession)

  useEffect(() => {
    initialize()
    const onSessionExpired = () => expireSession()
    window.addEventListener('wechat:session-expired', onSessionExpired)
    return () => window.removeEventListener('wechat:session-expired', onSessionExpired)
  }, [expireSession, initialize])

  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Route>
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<ChatPage />} />
        <Route path="/app/chat/:conversationId" element={<ChatPage />} />
      </Route>
      <Route path="/" element={<Navigate to="/app" replace />} />
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  )
}
