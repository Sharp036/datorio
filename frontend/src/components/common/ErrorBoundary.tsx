import { Component, type ErrorInfo, type ReactNode } from 'react'
import i18n from '@/i18n'
import { log } from '@/utils/logger'

interface Props {
  children: ReactNode
  /** Shown instead of the default message; a compact variant fits inside a widget */
  compact?: boolean
  /** A change of this value clears the error, for example a new report id or a re-render */
  resetKey?: unknown
}

interface State {
  error: Error | null
}

/**
 * Keeps a rendering error inside the page or the widget where it happened instead of
 * unmounting the whole application into a blank screen.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    log.error('Render error', { message: error.message, stack: info.componentStack })
  }

  componentDidUpdate(prev: Props) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null })
  }

  render() {
    if (!this.state.error) return this.props.children
    const message = this.state.error.message
    if (this.props.compact) {
      return (
        <div className="h-full flex flex-col items-center justify-center text-center p-3 text-xs text-red-500">
          <span>{i18n.t('common.render_error')}</span>
          <span className="mt-1 text-slate-400 break-all">{message}</span>
        </div>
      )
    }
    return (
      <div className="p-6 text-sm">
        <p className="font-medium text-red-600">{i18n.t('common.render_error')}</p>
        <p className="mt-1 text-slate-500 break-all">{message}</p>
        <button className="btn-secondary mt-3 text-xs" onClick={() => this.setState({ error: null })}>
          {i18n.t('common.retry')}
        </button>
      </div>
    )
  }
}
