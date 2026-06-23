<script setup lang="ts">
import {
  CheckCircleIcon,
  ClockIcon,
  XCircleIcon,
} from '@heroicons/vue/24/outline'
import type { DeploymentDetail, DeploymentStatus } from '../../api/projects'

const props = defineProps<{
  deployment: DeploymentDetail | null
  isLoading: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  close: []
}>()

function handleClose() {
  if (props.isLoading) {
    return
  }

  emit('close')
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function getStatusLabel(status: DeploymentStatus | string) {
  switch (status) {
    case 'SUCCEEDED':
      return '성공'
    case 'FAILED':
    case 'ROLLBACK_FAILED':
      return '실패'
    case 'QUEUED':
    case 'PENDING':
    case 'CLONING':
    case 'CHECKING_DOCKERFILE':
    case 'BUILDING':
    case 'PUSHING_IMAGE':
    case 'DEPLOYING':
    case 'HEALTH_CHECKING':
    case 'SWITCHING_TRAFFIC':
    case 'ROLLING_BACK':
      return '진행 중'
    case 'CANCELED':
      return '취소'
    case 'ROLLED_BACK':
      return '롤백 완료'
    default:
      return status
  }
}

function getStatusClass(status: DeploymentStatus | string) {
  switch (status) {
    case 'SUCCEEDED':
    case 'ROLLED_BACK':
      return 'success'
    case 'FAILED':
    case 'ROLLBACK_FAILED':
      return 'failed'
    case 'CANCELED':
      return 'inactive'
    default:
      return 'pending'
  }
}

function getTriggerTypeLabel(triggerType: string | undefined) {
  switch (triggerType) {
    case 'MANUAL':
      return '수동'
    case 'WEBHOOK':
      return 'Webhook'
    case 'ROLLBACK':
      return 'Rollback'
    default:
      return triggerType ?? '-'
  }
}
</script>

<template>
  <div class="modal-backdrop" role="presentation" @click.self="handleClose">
    <section class="panel modal-panel deployment-detail-modal">
      <header class="modal-header">
        <div class="deployment-detail-header-main">
          <div class="deployment-detail-header-title">
            <h2>배포 상세</h2>
            <span
              v-if="deployment && !isLoading && !errorMessage"
              class="deployment-history-status"
              :class="getStatusClass(deployment.status)"
            >
              <CheckCircleIcon v-if="getStatusClass(deployment.status) === 'success'" aria-hidden="true" />
              <XCircleIcon v-else-if="getStatusClass(deployment.status) === 'failed'" aria-hidden="true" />
              <ClockIcon v-else aria-hidden="true" />
              {{ getStatusLabel(deployment.status) }}
            </span>
          </div>
        </div>
        <button class="modal-close" type="button" aria-label="닫기" @click="handleClose">×</button>
      </header>

      <div v-if="isLoading" class="deployment-detail-message">상세 정보를 불러오는 중입니다.</div>
      <div v-else-if="errorMessage" class="deployment-detail-message error">{{ errorMessage }}</div>
      <template v-else-if="deployment">
        <dl class="detail-list compact deployment-detail-list">
          <div>
            <dt>배포 ID</dt>
            <dd>#{{ deployment.deploymentId }}</dd>
          </div>
          <div>
            <dt>프로젝트 ID</dt>
            <dd>{{ deployment.projectId ?? '-' }}</dd>
          </div>
          <div>
            <dt>이전 배포 ID</dt>
            <dd>{{ deployment.previousDeploymentId ?? '-' }}</dd>
          </div>
          <div>
            <dt>트리거</dt>
            <dd>{{ getTriggerTypeLabel(deployment.triggerType) }}</dd>
          </div>
          <div>
            <dt>브랜치</dt>
            <dd>{{ deployment.branch || '-' }}</dd>
          </div>
          <div>
            <dt>커밋 해시</dt>
            <dd>{{ deployment.commitHash || '-' }}</dd>
          </div>
          <div>
            <dt>커밋 메시지</dt>
            <dd>{{ deployment.commitMessage || '-' }}</dd>
          </div>
          <div>
            <dt>이미지 저장소</dt>
            <dd>{{ deployment.imageRepository || '-' }}</dd>
          </div>
          <div>
            <dt>이미지 태그</dt>
            <dd>{{ deployment.imageTag || '-' }}</dd>
          </div>
          <div>
            <dt>생성 시간</dt>
            <dd>{{ formatDateTime(deployment.createdAt) }}</dd>
          </div>
          <div>
            <dt>시작 시간</dt>
            <dd>{{ formatDateTime(deployment.startedAt) }}</dd>
          </div>
          <div>
            <dt>종료 시간</dt>
            <dd>{{ formatDateTime(deployment.finishedAt) }}</dd>
          </div>
          <div class="deployment-detail-wide">
            <dt>실패 사유</dt>
            <dd>{{ deployment.failureReason || '-' }}</dd>
          </div>
        </dl>
      </template>
    </section>
  </div>
</template>
