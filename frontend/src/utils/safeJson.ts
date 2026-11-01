/** JSON.parse that never throws: a missing, empty or broken value gives the fallback. */
export function parseJson<T>(raw: unknown, fallback: T): T {
  if (raw === null || raw === undefined || raw === '') return fallback
  if (typeof raw !== 'string') return raw as T
  try {
    const parsed = JSON.parse(raw)
    return (parsed === null || parsed === undefined) ? fallback : parsed as T
  } catch {
    return fallback
  }
}
