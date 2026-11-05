import { i18nReady } from './i18n'
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import App from './App'
import './index.css'

// After a new version is deployed the files of the old one are gone, and a page or a language not loaded yet
// fails to load in a tab opened before: reload once to get the new version (not again within a minute)
window.addEventListener('vite:preloadError', event => {
  let last = 0
  try { last = Number(sessionStorage.getItem('reloadedForNewVersion')) || 0 } catch { /* storage unavailable */ }
  if (Date.now() - last < 60_000) return
  try { sessionStorage.setItem('reloadedForNewVersion', String(Date.now())) } catch { return }
  event.preventDefault()
  window.location.reload()
})

// The first render waits for the translations of the chosen language, so the page does not start in English
i18nReady.catch(() => undefined).then(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <BrowserRouter>
        <App />
        <Toaster position="top-right" toastOptions={{
          className: '!bg-white dark:!bg-slate-800 !text-slate-900 dark:!text-slate-100 !shadow-lg',
          duration: 3000,
        }} />
      </BrowserRouter>
    </React.StrictMode>,
  )
})
