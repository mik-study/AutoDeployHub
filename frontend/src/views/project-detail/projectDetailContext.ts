import { inject, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import type { ProjectDetail } from '../../api/projects'

interface ProjectDetailContext {
  project: Ref<ProjectDetail | null>
  statusLabel: ComputedRef<string>
  formatDate: (value: string) => string
}

export const projectDetailContextKey: InjectionKey<ProjectDetailContext> = Symbol('projectDetailContext')

export function useProjectDetailContext() {
  const context = inject(projectDetailContextKey)

  if (!context) {
    throw new Error('Project detail context is not available.')
  }

  return context
}
