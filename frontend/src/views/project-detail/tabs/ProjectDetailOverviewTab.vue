<script setup lang="ts">
import { CheckCircleIcon } from '@heroicons/vue/24/outline'
import { useProjectDetailContext } from '../projectDetailContext'

const { project, statusLabel, formatDate } = useProjectDetailContext()
</script>

<template>
  <div v-if="project" class="overview-grid">
    <section class="panel detail-card summary-card overview-summary-card">
      <h2>배포 요약</h2>
      <dl class="detail-list compact">
        <div>
          <dt>현재 버전</dt>
          <dd>v1.2.2 <span class="version-color blue">(BLUE)</span></dd>
        </div>
        <div>
          <dt>이전 배포 버전</dt>
          <dd>v1.2.1 <span class="version-color green">(GREEN)</span></dd>
        </div>
        <div>
          <dt>최근 배포</dt>
          <dd>2025-05-16 14:30:22</dd>
        </div>
        <div>
          <dt>배포 상태</dt>
          <dd><span class="status-badge running">{{ statusLabel }}</span></dd>
        </div>
        <div>
          <dt>업타임</dt>
          <dd>2일 14시간 32분</dd>
        </div>
        <div>
          <dt>Health Check</dt>
          <dd class="health-ok"><CheckCircleIcon aria-hidden="true" /> 정상</dd>
        </div>
      </dl>
    </section>

    <section class="panel detail-card">
      <h2>서비스 정보</h2>
      <dl class="detail-list compact">
        <div>
          <dt>애플리케이션</dt>
          <dd>{{ project.name }}</dd>
        </div>
        <div>
          <dt>포트</dt>
          <dd>{{ project.healthCheckPort }}</dd>
        </div>
        <div>
          <dt>기본 브랜치</dt>
          <dd>{{ project.defaultBranch }}</dd>
        </div>
        <div>
          <dt>빌드 타입</dt>
          <dd>{{ project.buildType }}</dd>
        </div>
        <div>
          <dt>루트 디렉터리</dt>
          <dd>{{ project.rootDirectory || '/' }}</dd>
        </div>
        <div>
          <dt>생성일</dt>
          <dd>{{ formatDate(project.createdAt) }}</dd>
        </div>
      </dl>
    </section>

    <section class="panel detail-card resource-card">
      <h2>리소스 사용 현황</h2>
      <div class="resource-grid">
        <article class="resource-item blue">
          <div class="resource-ring"><span>23%</span></div>
          <strong>CPU</strong>
          <small>0.46 / 2 CPU</small>
        </article>
        <article class="resource-item green">
          <div class="resource-ring"><span>45%</span></div>
          <strong>메모리</strong>
          <small>920MB / 2GB</small>
        </article>
        <article class="resource-item dark">
          <div class="resource-ring"><span>31%</span></div>
          <strong>디스크</strong>
          <small>15GB / 50GB</small>
        </article>
        <article class="resource-item teal">
          <div class="resource-ring"><span>12%</span></div>
          <strong>네트워크</strong>
          <small>120Mbps</small>
        </article>
      </div>
    </section>
  </div>
</template>
