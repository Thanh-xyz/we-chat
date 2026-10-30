import { lazy, Suspense, useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute, PublicOnlyRoute } from './app/RouteGuards.jsx'
import { LoadingScreen } from './components/common/LoadingScreen.jsx'
import { useAuthStore } from './features/auth/authStore.js'
import { ChatPage } from './pages/ChatPage.jsx'

const ContactsPage = lazy(() => import('./pages/ContactsPage.jsx').then((module) => ({ default: module.ContactsPage })))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage.jsx').then((module) => ({ default: module.ForgotPasswordPage })))
const LoginPage = lazy(() => import('./pages/LoginPage.jsx').then((module) => ({ default: module.LoginPage })))
const RegisterPage = lazy(() => import('./pages/RegisterPage.jsx').then((module) => ({ default: module.RegisterPage })))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage.jsx').then((module) => ({ default: module.ResetPasswordPage })))
const SettingsPage = lazy(() => import('./pages/SettingsPage.jsx').then((module) => ({ default: module.SettingsPage })))
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage.jsx').then((module) => ({ default: module.VerifyEmailPage })))

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
    <Suspense fallback={<LoadingScreen label="Đang tải trang…" />}>
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
          <Route path="/app/contacts" element={<ContactsPage />} />
          <Route path="/app/settings" element={<SettingsPage />} />
        </Route>
        <Route path="/" element={<Navigate to="/app" replace />} />
        <Route path="*" element={<Navigate to="/app" replace />} />
      </Routes>
    </Suspense>
  )
}
