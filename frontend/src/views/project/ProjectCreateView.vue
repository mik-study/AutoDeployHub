<script setup lang="ts">
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createMockProject, createProject, type CreateProjectResponse } from '../../api/projects'

const router = useRouter()

const name = ref('')
const description = ref('')
const repositoryUrl = ref('')
const defaultBranch = ref('main')
const rootDirectory = ref('/')
const healthCheckPath = ref('/health')
const healthCheckPort = ref(8080)
const healthCheckTimeoutSeconds = ref(60)
const healthCheckIntervalSeconds = ref(5)
const subdomain = ref('')
const isSubmitting = ref(false)
const errorMessage = ref('')
const showAdvancedSettings = ref(false)
const createdProject = ref<CreateProjectResponse | null>(null)
const copiedField = ref<'url' | 'secret' | ''>('')

const canSubmit = computed(() => {
  return name.value.trim() && repositoryUrl.value.trim()
})

function buildPayload() {
  return {
    name: name.value.trim(),
    description: description.value.trim() || undefined,
    repositoryUrl: repositoryUrl.value.trim(),
    defaultBranch: defaultBranch.value.trim() || undefined,
    rootDirectory: rootDirectory.value.trim() || undefined,
    healthCheckPath: healthCheckPath.value.trim() || undefined,
    healthCheckPort: healthCheckPort.value,
    healthCheckTimeoutSeconds: healthCheckTimeoutSeconds.value,
    healthCheckIntervalSeconds: healthCheckIntervalSeconds.value,
    subdomain: subdomain.value.trim() || undefined,
  }
}

async function handleCreateProject() {
  errorMessage.value = ''

  if (!/^https:\/\/github\.com\/.+/.test(repositoryUrl.value.trim())) {
    errorMessage.value = 'GitHub 저장소 URL 형식으로 입력해주세요.'
    return
  }

  if (subdomain.value.trim() && !/^[a-z0-9-]{1,63}$/.test(subdomain.value.trim())) {
    errorMessage.value = '서브도메인은 소문자, 숫자, 하이픈만 사용할 수 있습니다.'
    return
  }

  isSubmitting.value = true

  try {
    const payload = buildPayload()
    const response = await createProject(payload)
    createdProject.value = response
  } catch {
    if (import.meta.env.DEV) {
      const response = createMockProject(buildPayload())
      createdProject.value = response
      return
    }

    errorMessage.value = '프로젝트 생성에 실패했습니다. 입력 정보를 확인해주세요.'
  } finally {
    isSubmitting.value = false
  }
}

async function goBack() {
  await router.push({ name: 'projects' })
}

async function goToProjects() {
  if (!createdProject.value) {
    await goBack()
    return
  }

  await router.push({
    name: 'projects',
    query: {
      created: '1',
      name: createdProject.value.name,
    },
  })
}

async function copyValue(value: string, field: 'url' | 'secret') {
  try {
    await navigator.clipboard.writeText(value)
    copiedField.value = field
  } catch {
    copiedField.value = ''
  }
}
</script>

<template>
  <section class="screen">
    <div class="screen-header">
      <div>
        <h1>새 프로젝트</h1>
      </div>
    </div>

    <section v-if="createdProject" class="panel project-form-panel webhook-result-panel">
      <div class="webhook-result-heading">
        <h2>프로젝트가 생성되었습니다</h2>
        <p>GitHub 저장소에 아래 Webhook 정보를 등록하세요.</p>
      </div>

      <div class="webhook-result-grid">
        <div class="field project-form-wide">
          <span class="field-label">프로젝트명</span>
          <div class="result-value">{{ createdProject.name }}</div>
        </div>

        <div class="field project-form-wide">
          <span class="field-label">Webhook URL</span>
          <div class="result-row">
            <div class="result-value">{{ createdProject.webhookUrl }}</div>
            <button class="secondary-button" type="button" @click="copyValue(createdProject.webhookUrl, 'url')">
              {{ copiedField === 'url' ? '복사됨' : '복사' }}
            </button>
          </div>
        </div>

        <div class="field project-form-wide">
          <span class="field-label">Webhook Secret</span>
          <div class="result-row">
            <div class="result-value">{{ createdProject.webhookSecret }}</div>
            <button class="secondary-button" type="button" @click="copyValue(createdProject.webhookSecret, 'secret')">
              {{ copiedField === 'secret' ? '복사됨' : '복사' }}
            </button>
          </div>
        </div>
      </div>

      <div class="form-actions">
        <button class="primary-button" type="button" @click="goToProjects">프로젝트 목록으로 이동</button>
      </div>
    </section>

    <form v-else class="panel project-form-panel" @submit.prevent="handleCreateProject">
      <div class="project-form-grid">
        <label class="field">
          <span class="field-label">프로젝트명</span>
          <input v-model="name" required type="text" placeholder="예: filehub" />
        </label>

        <label class="field">
          <span class="field-label">서브도메인</span>
          <input v-model="subdomain" type="text" placeholder="예: filehub" />
        </label>

        <label class="field project-form-wide">
          <span class="field-label">설명</span>
          <textarea v-model="description" rows="4" placeholder="프로젝트 설명을 입력하세요"></textarea>
        </label>

        <label class="field project-form-wide">
          <span class="field-label">GitHub 저장소 URL</span>
          <input
            v-model="repositoryUrl"
            required
            type="url"
            placeholder="https://github.com/myorg/filehub"
          />
        </label>

        <label class="field">
          <span class="field-label">기본 브랜치</span>
          <input v-model="defaultBranch" type="text" placeholder="main" />
        </label>

        <label class="field">
          <span class="field-label">루트 디렉터리</span>
          <input v-model="rootDirectory" type="text" placeholder="/" />
        </label>
      </div>

      <section class="advanced-settings">
        <button
          class="advanced-settings-toggle"
          type="button"
          :aria-expanded="showAdvancedSettings"
          @click="showAdvancedSettings = !showAdvancedSettings"
        >
          <span>고급 설정</span>
          <ChevronDownIcon class="advanced-settings-icon" :class="{ open: showAdvancedSettings }" aria-hidden="true" />
        </button>

        <div v-if="showAdvancedSettings" class="project-form-grid advanced-settings-grid">
          <label class="field">
            <span class="field-label">헬스 체크 경로</span>
            <input v-model="healthCheckPath" type="text" placeholder="/health" />
          </label>

          <label class="field">
            <span class="field-label">헬스 체크 포트</span>
            <input v-model.number="healthCheckPort" min="1" type="number" />
          </label>

          <label class="field">
            <span class="field-label">헬스 체크 타임아웃(초)</span>
            <input v-model.number="healthCheckTimeoutSeconds" min="1" type="number" />
          </label>

          <label class="field">
            <span class="field-label">헬스 체크 주기(초)</span>
            <input v-model.number="healthCheckIntervalSeconds" min="1" type="number" />
          </label>
        </div>
      </section>

      <p v-if="errorMessage" class="form-error project-feedback">{{ errorMessage }}</p>

      <div class="form-actions">
        <button class="secondary-button" type="button" @click="goBack">취소</button>
        <button class="primary-button" :disabled="isSubmitting || !canSubmit" type="submit">
          {{ isSubmitting ? '생성 중...' : '프로젝트 생성' }}
        </button>
      </div>
    </form>
  </section>
</template>
