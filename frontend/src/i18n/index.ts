import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// English is bundled as the fallback; every other locale is a separate file loaded when chosen
import en from './locales/en.json'

const localeLoaders = import.meta.glob<{ default: Record<string, string> }>(['./locales/*.json', '!./locales/en.json'])

const lazyLocales = {
  type: 'backend' as const,
  init() {},
  read(language: string, _namespace: string, callback: (error: unknown, data?: Record<string, string>) => void) {
    const load = localeLoaders[`./locales/${language}.json`]
    if (!load) { callback(null, {}); return }
    load().then(module => callback(null, module.default), error => callback(error))
  },
}

export interface SupportedLanguage {
  code: string
  nativeName: string
  dir?: 'rtl'
}

export const SUPPORTED_LANGUAGES: SupportedLanguage[] = [
  { code: 'en', nativeName: 'English' },
  { code: 'ru', nativeName: 'Русский' },
  { code: 'de', nativeName: 'Deutsch' },
  { code: 'fr', nativeName: 'Français' },
  { code: 'es', nativeName: 'Español' },
  { code: 'pt', nativeName: 'Português' },
  { code: 'zh', nativeName: '中文 (简体)' },
  { code: 'ja', nativeName: '日本語' },
  { code: 'ko', nativeName: '한국어' },
  { code: 'ar', nativeName: 'العربية', dir: 'rtl' },
  { code: 'hi', nativeName: 'हिन्दी' },
  { code: 'it', nativeName: 'Italiano' },
  { code: 'nl', nativeName: 'Nederlands' },
  { code: 'pl', nativeName: 'Polski' },
  { code: 'tr', nativeName: 'Türkçe' },
  { code: 'sv', nativeName: 'Svenska' },
  { code: 'cs', nativeName: 'Čeština' },
  { code: 'uk', nativeName: 'Українська' },
  { code: 'vi', nativeName: 'Tiếng Việt' },
  { code: 'th', nativeName: 'ไทย' },
  { code: 'id', nativeName: 'Bahasa Indonesia' },
  { code: 'ro', nativeName: 'Română' },
  { code: 'hu', nativeName: 'Magyar' },
  { code: 'el', nativeName: 'Ελληνικά' },
  { code: 'fi', nativeName: 'Suomi' },
  { code: 'da', nativeName: 'Dansk' },
  { code: 'no', nativeName: 'Norsk' },
  { code: 'he', nativeName: 'עברית', dir: 'rtl' },
]

const savedLang = localStorage.getItem('language')
const browserLang = navigator.language.split('-')[0]
const initialLang = savedLang || (SUPPORTED_LANGUAGES.some(l => l.code === browserLang) ? browserLang : 'en')

// Resolves when the initial language is loaded (or failed to load and English is shown)
export const i18nReady = i18n
  .use(lazyLocales)
  .use(initReactI18next)
  .init({
    resources: { en: { translation: en } },
    partialBundledLanguages: true,
    lng: initialLang,
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
  })

i18n.on('languageChanged', (lng) => {
  const lang = SUPPORTED_LANGUAGES.find(l => l.code === lng)
  document.documentElement.dir = lang?.dir === 'rtl' ? 'rtl' : 'ltr'
  document.documentElement.lang = lng
})

// Set initial lang attribute
document.documentElement.lang = initialLang

export default i18n
