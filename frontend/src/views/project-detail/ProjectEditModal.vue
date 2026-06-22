<script setup lang="ts">
import { ref, watch } from 'vue'
import type { UpdateProjectRequest } from '../../api/projects'

const props = defineProps<{
  initialValue: UpdateProjectRequest
  isSaving: boolean
  projectName: string
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: UpdateProjectRequest]
}>()

const form = ref<UpdateProjectRequest>({
  name: '',
  description: '',
  defaultBranch: '',
  rootDirectory: '',
  healthCheckPath: '',
  healthCheckPort: 8080,
  healthCheckTimeoutSeconds: 30,
  healthCheckIntervalSeconds: 10,
})

function syncForm() {
  form.value = {
    name: props.initialValue.name ?? '',
    description: props.initialValue.description ?? '',
    defaultBranch: props.initialValue.defaultBranch ?? '',
    rootDirectory: props.initialValue.rootDirectory ?? '',
    healthCheckPath: props.initialValue.healthCheckPath ?? '',
    healthCheckPort: props.initialValue.healthCheckPort ?? 8080,
    healthCheckTimeoutSeconds: props.initialValue.healthCheckTimeoutSeconds ?? 30,
    healthCheckIntervalSeconds: props.initialValue.healthCheckIntervalSeconds ?? 10,
  }
}

function handleClose() {
  if (props.isSaving) {
    return
  }

  emit('close')
}

function handleSubmit() {
  emit('submit', { ...form.value })
}

watch(() => props.initialValue, syncForm, { immediate: true, deep: true })
</script>

<template>
  <div class="modal-backdrop" role="presentation" @click.self="handleClose">
    <form class="panel modal-panel project-edit-modal" @submit.prevent="handleSubmit">
      <header class="modal-header">
        <div>
          <h2>프로젝트 수정</h2>
        </div>
        <button class="modal-close" type="button" aria-label="닫기" @click="handleClose">×</button>
      </header>

      <div class="project-form-grid">
        <label class="field project-form-wide">
          <span class="field-label">프로젝트명</span>
          <input v-model="form.name" required type="text" />
        </label>

        <label class="field project-form-wide">
          <span class="field-label">설명</span>
          <textarea v-model="form.description" rows="2"></textarea>
        </label>

        <label class="field">
          <span class="field-label">기본 브랜치</span>
          <input v-model="form.defaultBranch" type="text" />
        </label>

        <label class="field">
          <span class="field-label">루트 디렉터리</span>
          <input v-model="form.rootDirectory" type="text" />
        </label>

        <label class="field">
          <span class="field-label">헬스 체크 경로</span>
          <input v-model="form.healthCheckPath" type="text" />
        </label>

        <label class="field">
          <span class="field-label">헬스 체크 포트</span>
          <input v-model.number="form.healthCheckPort" min="1" type="number" />
        </label>

        <label class="field">
          <span class="field-label">헬스 체크 타임아웃(초)</span>
          <input v-model.number="form.healthCheckTimeoutSeconds" min="1" type="number" />
        </label>

        <label class="field">
          <span class="field-label">헬스 체크 주기(초)</span>
          <input v-model.number="form.healthCheckIntervalSeconds" min="1" type="number" />
        </label>
      </div>

      <div class="form-actions">
        <button class="secondary-button" type="button" @click="handleClose">취소</button>
        <button class="primary-button" :disabled="isSaving || !form.name?.trim()" type="submit">
          {{ isSaving ? '저장 중...' : '저장' }}
        </button>
      </div>
    </form>
  </div>
</template>
