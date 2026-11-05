import { Suspense } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'
import ErrorBoundary from '@/components/common/ErrorBoundary'
import LoadingSpinner from '@/components/common/LoadingSpinner'

export default function AppLayout() {
  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex flex-col flex-1 overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto p-6 bg-surface-50 dark:bg-dark-surface-0">
          <ErrorBoundary>
            {/* A page loading for the first time keeps the sidebar and the header on screen */}
            <Suspense fallback={<LoadingSpinner className="h-full" />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  )
}
