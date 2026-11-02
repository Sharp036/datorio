import { Sun, Moon, LogOut, User, Globe, BookOpen, Info, Hash } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'
import { useDesignerStore } from '@/store/useDesignerStore'
import { useState, useRef, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { authApi } from '@/api/auth'
import { FORMAT_LOCALES, formatLocale, setFormatLocale, formatLocaleName, formatLocaleSample } from '@/utils/formatLocale'
import GlobalSearchBar from '@/components/search/GlobalSearchBar'
import Logo from '@/components/common/Logo'
import clsx from 'clsx'

export default function Header() {
  const { user, logout } = useAuthStore()
  const { isDark, toggle } = useThemeStore()
  const { t, i18n } = useTranslation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [langOpen, setLangOpen] = useState(false)
  const [formatOpen, setFormatOpen] = useState(false)
  const [aboutOpen, setAboutOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const langRef = useRef<HTMLDivElement>(null)
  const formatRef = useRef<HTMLDivElement>(null)
  const location = useLocation()

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
      if (langRef.current && !langRef.current.contains(e.target as Node)) setLangOpen(false)
      if (formatRef.current && !formatRef.current.contains(e.target as Node)) setFormatOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const handleLanguageChange = async (code: string) => {
    i18n.changeLanguage(code)
    localStorage.setItem('language', code)
    setLangOpen(false)
    try {
      await authApi.updateLanguage(code)
    } catch {
      // Language is already set locally
    }
  }

  // Charts and tables keep the format they were built with, so a new format reloads the page
  const handleFormatChange = (code: string | undefined) => {
    setFormatOpen(false)
    if (code === formatLocale) return
    const inDesigner = /^\/reports\/(new|[^/]+\/edit)$/.test(location.pathname)
    if (inDesigner && useDesignerStore.getState().dirty && !window.confirm(t('header.format_reload_confirm'))) return
    setFormatLocale(code)
    window.location.reload()
  }

  return (
    <header className="h-14 flex items-center justify-between px-6 bg-white dark:bg-dark-surface-50
                        border-b border-surface-200 dark:border-dark-surface-100 flex-shrink-0">
      <GlobalSearchBar />

      <div className="flex items-center gap-2">
        {/* Theme toggle */}
        <button onClick={toggle} className="btn-ghost p-2 rounded-lg">
          {isDark ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
        </button>

        {/* Language selector */}
        <div className="relative" ref={langRef}>
          <button onClick={() => setLangOpen(!langOpen)} className="btn-ghost p-2 rounded-lg" title={t('header.language')}>
            <Globe className="w-5 h-5" />
          </button>
          {langOpen && (
            <div className="absolute right-0 mt-2 w-56 bg-white dark:bg-dark-surface-50 rounded-xl shadow-xl border border-surface-200 dark:border-dark-surface-100 p-1 z-50 max-h-80 overflow-y-auto">
              {SUPPORTED_LANGUAGES.map(lang => (
                <button
                  key={lang.code}
                  onClick={() => handleLanguageChange(lang.code)}
                  className={clsx(
                    'flex items-center justify-between w-full px-3 py-2 text-sm rounded-lg',
                    i18n.language === lang.code
                      ? 'bg-brand-50 dark:bg-brand-900/30 text-brand-700 dark:text-brand-400'
                      : 'hover:bg-surface-100 dark:hover:bg-dark-surface-100 text-slate-700 dark:text-slate-300'
                  )}
                >
                  <span>{lang.nativeName}</span>
                  <span className="text-xs text-slate-400">{lang.code.toUpperCase()}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Number and date format */}
        <div className="relative" ref={formatRef}>
          <button onClick={() => setFormatOpen(!formatOpen)} className="btn-ghost p-2 rounded-lg" title={t('header.format')}>
            <Hash className="w-5 h-5" />
          </button>
          {formatOpen && (
            <div className="absolute right-0 mt-2 w-96 bg-white dark:bg-dark-surface-50 rounded-xl shadow-xl border border-surface-200 dark:border-dark-surface-100 p-1 z-50 max-h-80 overflow-y-auto">
              <div className="px-3 py-2 text-xs font-medium text-slate-500 dark:text-slate-400">{t('header.format')}</div>
              {[undefined, ...FORMAT_LOCALES].map(code => {
                const [num, date] = formatLocaleSample(code)
                return (
                  <button
                    key={code ?? 'auto'}
                    onClick={() => handleFormatChange(code)}
                    className={clsx(
                      'flex items-center justify-between gap-3 w-full px-3 py-2 text-sm rounded-lg',
                      formatLocale === code
                        ? 'bg-brand-50 dark:bg-brand-900/30 text-brand-700 dark:text-brand-400'
                        : 'hover:bg-surface-100 dark:hover:bg-dark-surface-100 text-slate-700 dark:text-slate-300'
                    )}
                  >
                    <span className="truncate min-w-0">{code ? formatLocaleName(code) : t('header.format_auto')}</span>
                    <span className="flex gap-2 text-xs text-slate-400 whitespace-nowrap">
                      <span>{num}</span><span>{date}</span>
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* User menu */}
        <div className="relative" ref={menuRef}>
          <button
            onClick={() => setMenuOpen(!menuOpen)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-surface-100 dark:hover:bg-dark-surface-100 transition-colors"
          >
            <div className="w-7 h-7 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center">
              <User className="w-4 h-4 text-brand-600 dark:text-brand-400" />
            </div>
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
              {user?.username || t('common.user')}
            </span>
          </button>

          {menuOpen && (
            <div className="absolute right-0 mt-2 w-48 card p-1 z-50 shadow-lg">
              <div className="px-3 py-2 text-xs text-slate-500 dark:text-slate-400 border-b border-surface-200 dark:border-dark-surface-100">
                {user?.roles?.join(', ') || t('common.no_roles')}
              </div>
              <a
                href="/dashboard-guide.html"
                target="_blank"
                rel="noopener noreferrer"
                onClick={() => setMenuOpen(false)}
                className="flex items-center gap-2 w-full px-3 py-2 text-sm text-slate-700 dark:text-slate-300
                           hover:bg-surface-100 dark:hover:bg-dark-surface-100 rounded-lg mt-1"
              >
                <BookOpen className="w-4 h-4" /> {t('header.documentation')}
              </a>
              <button
                onClick={() => { setMenuOpen(false); setAboutOpen(true) }}
                className="flex items-center gap-2 w-full px-3 py-2 text-sm text-slate-700 dark:text-slate-300
                           hover:bg-surface-100 dark:hover:bg-dark-surface-100 rounded-lg"
              >
                <Info className="w-4 h-4" /> {t('header.about')}
              </button>
              <div className="border-t border-surface-200 dark:border-dark-surface-100 mt-1" />
              <button
                onClick={() => { logout(); setMenuOpen(false) }}
                className="flex items-center gap-2 w-full px-3 py-2 text-sm text-red-600 dark:text-red-400
                           hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg mt-1"
              >
                <LogOut className="w-4 h-4" /> {t('auth.sign_out')}
              </button>
            </div>
          )}
        </div>
      </div>

      {aboutOpen && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setAboutOpen(false)}>
          <div className="bg-white dark:bg-dark-surface-50 rounded-xl shadow-2xl w-full max-w-md p-6" onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-3 mb-4">
              <Logo className="w-10 h-10 flex-shrink-0" />
              <div>
                <h3 className="text-lg font-semibold text-slate-800 dark:text-white">Datorio BI Platform</h3>
                <p className="text-xs text-slate-400">{t('header.about_version', { version: __APP_VERSION__ })}</p>
              </div>
            </div>
            <div className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
              <p><strong>Frontend:</strong> React, TypeScript, Vite, TailwindCSS, ECharts</p>
              <p><strong>Backend:</strong> Spring Boot, Kotlin, PostgreSQL</p>
              <p><strong>{t('header.about_charts')}:</strong> 17</p>
              <p><strong>{t('header.about_languages')}:</strong> 28</p>
            </div>
            <div className="flex justify-between items-center mt-5">
              <a
                href="/dashboard-guide.html"
                target="_blank"
                rel="noopener noreferrer"
                className="text-sm text-brand-600 dark:text-brand-400 hover:underline"
              >
                {t('header.documentation')}
              </a>
              <button onClick={() => setAboutOpen(false)} className="btn-secondary text-sm">{t('common.close')}</button>
            </div>
          </div>
        </div>
      )}
    </header>
  )
}
