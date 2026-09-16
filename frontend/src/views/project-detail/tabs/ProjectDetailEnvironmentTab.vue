<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  EyeIcon,
  EyeSlashIcon,
  KeyIcon,
  PencilSquareIcon,
  PlusIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import {
  createEnvironmentVariable,
  deleteEnvironmentVariable,
  getEnvironmentVariables,
  updateEnvironmentVariable,
  type CreateEnvironmentVariableRequest,
  type EnvironmentVariableItem,
} from '../../../api/environmentVariables'

const ENV_KEY_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/
const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))
const environmentVariables = ref<EnvironmentVariableItem[]>([])
const isLoading = ref(false)
const isSaving = ref(false)
const deletingEnvIds = ref<number[]>([])
const errorMessage = ref('')
const successMessage = ref('')
const isFormOpen = ref(false)
const editingVariable = ref<EnvironmentVariableItem | null>(null)
const showValue = ref(false)
const form = ref<CreateEnvironmentVariableRequest>({
  key: '',
  value: '',
  isSecret: true,
})

const formTitle = computed(() => editingVariable.value ? '환경변수 값 변경' : '환경변수 추가')
const isFormValid = computed(() => {
  const keyValid = editingVariable.value !== null || ENV_KEY_PATTERN.test(form.value.key.trim())
  return keyValid && form.value.value.length > 0
})

async function loadEnvironmentVariables() {
  if (!Number.isFinite(projectId.value)) {
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    environmentVariables.value = await getEnvironmentVariables(projectId.value)
  } catch {
    errorMessage.value = '환경변수 목록을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

function openCreateForm() {
  editingVariable.value = null
  form.value = { key: '', value: '', isSecret: true }
  showValue.value = false
  errorMessage.value = ''
  successMessage.value = ''
  isFormOpen.value = true
}

function openEditForm(variable: EnvironmentVariableItem) {
  editingVariable.value = variable
  form.value = {
    key: variable.key,
    value: '',
    isSecret: variable.isSecret,
  }
  showValue.value = false
  errorMessage.value = ''
  successMessage.value = ''
  isFormOpen.value = true
}

function closeForm() {
  if (isSaving.value) {
    return
  }

  isFormOpen.value = false
  editingVariable.value = null
}

async function handleSubmit() {
  if (!isFormValid.value || !Number.isFinite(projectId.value)) {
    return
  }

  isSaving.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    if (editingVariable.value) {
      await updateEnvironmentVariable(projectId.value, editingVariable.value.envId, {
        value: form.value.value,
      })
      successMessage.value = `${editingVariable.value.key} 값이 변경되었습니다.`
    } else {
      await createEnvironmentVariable(projectId.value, {
        key: form.value.key.trim(),
        value: form.value.value,
        isSecret: form.value.isSecret,
      })
      successMessage.value = `${form.value.key.trim()} 환경변수가 추가되었습니다.`
    }

    isFormOpen.value = false
    editingVariable.value = null
    await loadEnvironmentVariables()
  } catch {
    errorMessage.value = editingVariable.value
      ? '환경변수 값을 변경하지 못했습니다.'
      : '환경변수를 추가하지 못했습니다. 키 중복 여부를 확인해주세요.'
  } finally {
    isSaving.value = false
  }
}

async function handleDelete(variable: EnvironmentVariableItem) {
  if (deletingEnvIds.value.includes(variable.envId)) {
    return
  }

  if (!window.confirm(`${variable.key} 환경변수를 삭제하시겠습니까?`)) {
    return
  }

  deletingEnvIds.value = [...deletingEnvIds.value, variable.envId]
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await deleteEnvironmentVariable(projectId.value, variable.envId)
    successMessage.value = `${variable.key} 환경변수가 삭제되었습니다.`
    await loadEnvironmentVariables()
  } catch {
    errorMessage.value = '환경변수를 삭제하지 못했습니다.'
  } finally {
    deletingEnvIds.value = deletingEnvIds.value.filter((envId) => envId !== variable.envId)
  }
}

function formatUpdatedAt(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

onMounted(() => {
  void loadEnvironmentVariables()
})

watch(projectId, () => {
  void loadEnvironmentVariables()
})
</script>

<template>
  <section class="panel environment-panel">
    <header class="environment-header">
      <div>
        <h2>환경 변수</h2>
        <p>배포 컨테이너에 주입할 값을 관리합니다. Secret 값은 저장 후 다시 표시되지 않습니다.</p>
      </div>
      <button class="primary-button" type="button" @click="openCreateForm">
        <PlusIcon class="button-icon" aria-hidden="true" />
        환경변수 추가
      </button>
    </header>

    <p v-if="successMessage" class="form-success environment-feedback">{{ successMessage }}</p>
    <p v-if="errorMessage" class="form-error environment-feedback">{{ errorMessage }}</p>

    <div class="environment-table-wrap">
      <table class="data-table environment-table">
        <thead>
          <tr>
            <th>키</th>
            <th>값</th>
            <th>유형</th>
            <th>수정일</th>
            <th class="environment-actions-heading">작업</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="isLoading">
            <td colspan="5" class="table-message">환경변수를 불러오는 중입니다.</td>
          </tr>
          <tr v-else-if="environmentVariables.length === 0 && !errorMessage">
            <td colspan="5" class="table-message environment-empty">
              <KeyIcon aria-hidden="true" />
              <strong>등록된 환경변수가 없습니다.</strong>
              <span>첫 환경변수를 추가해 배포 설정을 시작하세요.</span>
            </td>
          </tr>
          <tr v-for="variable in environmentVariables" :key="variable.envId">
            <td><code class="environment-key">{{ variable.key }}</code></td>
            <td><code class="environment-value">{{ variable.value }}</code></td>
            <td>
              <span class="environment-type" :class="{ secret: variable.isSecret }">
                {{ variable.isSecret ? 'Secret' : '일반' }}
              </span>
            </td>
            <td>{{ formatUpdatedAt(variable.updatedAt) }}</td>
            <td>
              <div class="environment-row-actions">
                <button
                  class="environment-icon-button"
                  type="button"
                  :aria-label="`${variable.key} 값 변경`"
                  title="값 변경"
                  @click="openEditForm(variable)"
                >
                  <PencilSquareIcon aria-hidden="true" />
                </button>
                <button
                  class="environment-icon-button danger"
                  type="button"
                  :disabled="deletingEnvIds.includes(variable.envId)"
                  :aria-label="`${variable.key} 삭제`"
                  title="삭제"
                  @click="handleDelete(variable)"
                >
                  <TrashIcon aria-hidden="true" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="isFormOpen" class="modal-backdrop" role="presentation" @click.self="closeForm">
      <form class="panel modal-panel environment-modal" @submit.prevent="handleSubmit">
        <header class="modal-header">
          <div>
            <h2>{{ formTitle }}</h2>
            <p v-if="editingVariable">키는 변경할 수 없으며 새 값만 저장됩니다.</p>
            <p v-else>키는 영문자 또는 밑줄로 시작해야 합니다.</p>
          </div>
          <button class="modal-close" type="button" aria-label="닫기" @click="closeForm">×</button>
        </header>

        <div class="environment-form-grid">
          <label class="field">
            <span class="field-label">키</span>
            <input
              v-model="form.key"
              :disabled="Boolean(editingVariable)"
              maxlength="100"
              placeholder="DATABASE_URL"
              required
              spellcheck="false"
              type="text"
            />
            <small v-if="!editingVariable && form.key && !ENV_KEY_PATTERN.test(form.key.trim())" class="field-error">
              영문자 또는 밑줄로 시작하고 영문자, 숫자, 밑줄만 사용할 수 있습니다.
            </small>
          </label>

          <label class="field">
            <span class="field-label">값</span>
            <span class="environment-value-input">
              <input
                v-model="form.value"
                :type="showValue ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="환경변수 값을 입력하세요"
                required
                spellcheck="false"
              />
              <button
                type="button"
                :aria-label="showValue ? '값 숨기기' : '값 보기'"
                @click="showValue = !showValue"
              >
                <EyeSlashIcon v-if="showValue" aria-hidden="true" />
                <EyeIcon v-else aria-hidden="true" />
              </button>
            </span>
          </label>

          <label v-if="!editingVariable" class="environment-secret-option">
            <input v-model="form.isSecret" type="checkbox" />
            <span>
              <strong>Secret으로 저장</strong>
              <small>목록과 API 응답에서 값이 <code>****</code>로 마스킹됩니다.</small>
            </span>
          </label>
        </div>

        <div class="form-actions">
          <button class="secondary-button" type="button" @click="closeForm">취소</button>
          <button class="primary-button" :disabled="isSaving || !isFormValid" type="submit">
            {{ isSaving ? '저장 중...' : '저장' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
