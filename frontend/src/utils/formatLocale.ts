/**
 * Locale of numbers and dates, chosen by the user in the header apart from the interface language.
 * Unset, values are formatted by the browser's locale. It is read once: a change reloads the page,
 * so charts and tables built before it do not keep the previous format.
 */

const STORAGE_KEY = 'formatLocale'

// The interface languages with their main region, and regions whose format differs
export const FORMAT_LOCALES = [
  'en-US', 'en-GB', 'ru-RU', 'de-DE', 'de-CH', 'fr-FR', 'es-ES', 'es-MX', 'pt-BR', 'pt-PT',
  'zh-CN', 'ja-JP', 'ko-KR', 'ar-SA', 'hi-IN', 'it-IT', 'nl-NL', 'pl-PL', 'tr-TR', 'sv-SE',
  'cs-CZ', 'uk-UA', 'vi-VN', 'th-TH', 'id-ID', 'ro-RO', 'hu-HU', 'el-GR', 'fi-FI', 'da-DK',
  'nb-NO', 'he-IL',
]

const stored = localStorage.getItem(STORAGE_KEY)

/** Pass as the locale argument of toLocaleString and Intl; undefined means the browser's locale */
export const formatLocale: string | undefined = stored && FORMAT_LOCALES.includes(stored) ? stored : undefined

export function setFormatLocale(code: string | undefined) {
  if (code) localStorage.setItem(STORAGE_KEY, code)
  else localStorage.removeItem(STORAGE_KEY)
}

/** Name of the locale in its own language, e.g. "Deutsch (Schweiz)" */
export function formatLocaleName(code: string): string {
  try {
    return new Intl.DisplayNames([code], { type: 'language', languageDisplay: 'standard' }).of(code) ?? code
  } catch {
    return code
  }
}

const SAMPLE_DATE = new Date(new Date().getFullYear(), 11, 31)

/** A number and a date in the locale, to show the difference between the options */
export function formatLocaleSample(code: string | undefined): [string, string] {
  return [(1234567.89).toLocaleString(code), SAMPLE_DATE.toLocaleDateString(code)]
}
