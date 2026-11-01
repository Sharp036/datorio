import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import LoadingSpinner from '@/components/common/LoadingSpinner'

export default function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuthStore()
  const location = useLocation()

  if (isLoading) return <LoadingSpinner className="min-h-screen" />
  // The login page sends the user back to the page they asked for
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />

  return <Outlet />
}
