import { Routes, Route } from 'react-router-dom'
import { lazy, Suspense, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/store/authStore'

import ProtectedRoute from '@/components/auth/ProtectedRoute'
import PermissionRoute from '@/components/auth/PermissionRoute'
import LoginPage from '@/components/auth/LoginPage'
import AppLayout from '@/components/layout/AppLayout'
import LoadingSpinner from '@/components/common/LoadingSpinner'

// Pages are separate files loaded on first visit, so the first load does not carry the whole application
const ScheduleListPage = lazy(() => import('@/components/dashboard/ScheduleListPage'))
const ReportListPage = lazy(() => import('@/components/reports/ReportListPage'))
const ReportViewerPage = lazy(() => import('@/components/reports/ReportViewerPage'))
const QueryListPage = lazy(() => import('@/components/queries/QueryListPage'))
const DataSourceListPage = lazy(() => import('@/components/datasources/DataSourceListPage'))
const ScriptEditorPage = lazy(() => import('@/components/scripts/ScriptEditorPage'))
const ReportDesignerPage = lazy(() => import('@/components/designer/ReportDesignerPage'))
const EmbedViewerPage = lazy(() => import('@/components/embed/EmbedViewerPage'))
const AlertsPage = lazy(() => import('@/components/alerts/AlertsPage'))
const MonitoringPage = lazy(() => import('@/components/monitoring/MonitoringPage'))
const AdminPage = lazy(() => import('@/components/admin/AdminPage'))
const ChangePassword = lazy(() => import('@/components/admin/ChangePassword'))
const SharedWithMePage = lazy(() => import('@/components/sharing/SharedWithMePage'))
const WorkspacePage = lazy(() => import('@/components/workspace/WorkspacePage'))
const ModelListPage = lazy(() => import('@/components/modeling/ModelListPage'))
const ModelEditorPage = lazy(() => import('@/components/modeling/ModelEditorPage'))
const ExplorePage = lazy(() => import('@/components/modeling/ExplorePage'))
const TemplateGalleryPage = lazy(() => import('@/components/templates/TemplateGalleryPage'))
const ImportPage = lazy(() => import('@/components/imports/ImportPage'))

function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <div className="min-h-screen flex items-center justify-center text-slate-500">
      <div className="text-center">
        <h1 className="text-6xl font-bold mb-4">404</h1>
        <p>{t('common.not_found.message')}</p>
      </div>
    </div>
  )
}

export default function App() {
  const checkAuth = useAuthStore(s => s.checkAuth)

  useEffect(() => { checkAuth() }, [checkAuth])

  return (
    <Suspense fallback={<LoadingSpinner className="min-h-screen" />}>
      <Routes>
        {/* Public */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/embed/:token" element={<EmbedViewerPage />} />

        {/* Protected */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/" element={<WorkspacePage />} />
            <Route
              path="/reports"
              element={
                <PermissionRoute permission="REPORT_VIEW">
                  <ReportListPage />
                </PermissionRoute>
              }
            />
            <Route path="/reports/new" element={<ReportDesignerPage />} />
            <Route path="/reports/:id/edit" element={<ReportDesignerPage />} />
            <Route path="/reports/:id" element={<ReportViewerPage />} />
            <Route path="/queries" element={<QueryListPage />} />
            <Route
              path="/datasources"
              element={
                <PermissionRoute permission="DATASOURCE_VIEW">
                  <DataSourceListPage />
                </PermissionRoute>
              }
            />
            <Route path="/schedules" element={<ScheduleListPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/monitoring" element={<MonitoringPage />} />
            <Route path="/scripts" element={<ScriptEditorPage />} />
            <Route
              path="/admin"
              element={
                <PermissionRoute permission="USER_MANAGE">
                  <AdminPage />
                </PermissionRoute>
              }
            />
            <Route path="/profile/password" element={<ChangePassword />} />
            <Route path="/shared" element={<SharedWithMePage />} />
            <Route path="/models" element={<ModelListPage />} />
            <Route path="/models/:id" element={<ModelEditorPage />} />
            <Route path="/explore/:modelId" element={<ExplorePage />} />
            <Route path="/templates" element={<TemplateGalleryPage />} />
            <Route path="/import" element={<ImportPage />} />
          </Route>
        </Route>

        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}
