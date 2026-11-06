import { create } from 'zustand'
import { JobStatusResponse } from '@/api/controls'

/** The run started by a RUN_JOB button: kept outside the button, so a widget that is unmounted (a tab change,
 *  a layout change) and mounted again shows the same run and keeps asking for its state. */
export interface JobProgress {
  runId: string
  startedAt: number
  status?: JobStatusResponse
  pollErrors: number
  /** The end of the run was already announced (toast, report refresh) */
  notified?: boolean
}

interface JobRunState {
  /** By report id and widget id */
  jobs: Record<string, JobProgress | null>
  setJob: (key: string, update: JobProgress | null | ((job: JobProgress | null) => JobProgress | null)) => void
}

export const jobRunKey = (reportId: number, widgetId?: number) => `${reportId}:${widgetId ?? ''}`

export const useJobRunStore = create<JobRunState>((set, get) => ({
  jobs: {},
  setJob: (key, update) => {
    const current = get().jobs[key] ?? null
    const next = typeof update === 'function' ? update(current) : update
    if (next === current) return
    set(state => ({ jobs: { ...state.jobs, [key]: next } }))
  },
}))
