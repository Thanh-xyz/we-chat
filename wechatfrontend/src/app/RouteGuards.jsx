import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '../features/auth/authStore.js'
import { LoadingScreen } from '../components/common/LoadingScreen.jsx'

export function ProtectedRoute() {
  const status = useAuthStore((state) => state.status)
  const location = useLocation()

  if (status === 'checking') return <LoadingScreen label="Đang khôi phục phiên đăng nhập…" />
  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

export function PublicOnlyRoute() {
  const status = useAuthStore((state) => state.status)
  if (status === 'checking') return <LoadingScreen label="Đang tải…" />
  return status === 'authenticated' ? <Navigate to="/app" replace /> : <Outlet />
}
