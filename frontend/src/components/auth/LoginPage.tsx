import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import Logo from '@/components/common/Logo'
import { LogIn, Eye, EyeOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import toast from 'react-hot-toast'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const login = useAuthStore(s => s.login)
  const navigate = useNavigate()
  const location = useLocation()
  const { t, i18n } = useTranslation()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      await login({ username, password })
      toast.success(t('auth.welcome_back'))
      // Back to the page that asked for the login: the route guard passes it in the state, the session expiry in ?next=
      const next = (location.state as { from?: string } | null)?.from
        || new URLSearchParams(location.search).get('next')
      navigate(next && next.startsWith('/') && !next.startsWith('/login') ? next : '/', { replace: true })
    } catch (err: unknown) {
      const response = (err as { response?: { status?: number; data?: { message?: string } } })?.response
      // 401 is a wrong user name or password; no answer or a 5xx means the server is not available
      const msg = response?.status === 401 ? t('auth.invalid_credentials')
        : !response?.status || response.status >= 500 ? t('auth.server_unavailable')
          : response.data?.message || t('auth.invalid_credentials')
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-50 dark:bg-dark-surface-0 px-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <Logo className="w-14 h-14 mb-3" />
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">Datorio</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{t('auth.sign_in_subtitle')}</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="card p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('auth.username')}</label>
            <input
              type="text" value={username} onChange={e => setUsername(e.target.value)}
              className="input" placeholder={t('auth.username')} autoFocus required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('auth.password')}</label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'} value={password}
                onChange={e => setPassword(e.target.value)}
                className="input pr-10" placeholder="••••••••" required
              />
              <button
                type="button" onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          <button type="submit" disabled={loading || !username || !password} className="btn-primary w-full">
            <LogIn className="w-4 h-4" />
            {loading ? t('auth.signing_in') : t('auth.sign_in')}
          </button>
        </form>

        {/* Language selector */}
        <select
          value={i18n.language}
          onChange={e => {
            i18n.changeLanguage(e.target.value)
            localStorage.setItem('language', e.target.value)
          }}
          className="mt-4 mx-auto block text-sm text-slate-500 dark:text-slate-400 bg-transparent border border-surface-200 dark:border-dark-surface-100 rounded-lg px-3 py-1.5 cursor-pointer"
        >
          {SUPPORTED_LANGUAGES.map(lang => (
            <option key={lang.code} value={lang.code}>{lang.nativeName}</option>
          ))}
        </select>
      </div>
    </div>
  )
}
