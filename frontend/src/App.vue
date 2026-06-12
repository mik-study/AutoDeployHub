<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRightStartOnRectangleIcon,
  BellIcon,
  Cog6ToothIcon,
  FolderIcon,
  LinkIcon,
  UserCircleIcon,
  UserGroupIcon,
} from '@heroicons/vue/24/outline'
import { useAuthStore } from './stores/auth'
import logoIconImage from './assets/images/autodeployhub-logo-icon.png'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isAuthLayout = computed(() => route.meta.layout === 'auth')
const currentUser = computed(() => {
  return authStore.user ?? {
    name: 'user',
    email: '',
  }
})

async function handleLogout() {
  if (!window.confirm('로그아웃하시겠습니까?')) {
    return
  }

  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <RouterView v-if="isAuthLayout" />

  <div v-else class="app-layout">
    <aside class="sidebar">
      <RouterLink class="sidebar-brand" to="/projects">
        <span class="brand-mark" aria-hidden="true">
          <img :src="logoIconImage" alt="" />
        </span>
        <span>AutoDeploy<span>Hub</span></span>
      </RouterLink>

      <nav class="sidebar-nav" aria-label="Primary">
        <RouterLink class="sidebar-nav-item sidebar-nav-projects" to="/projects">
          <FolderIcon class="nav-icon" aria-hidden="true" />
          프로젝트
        </RouterLink>
        <RouterLink class="sidebar-nav-item sidebar-nav-integration-channels" to="/integration-channels">
          <LinkIcon class="nav-icon" aria-hidden="true" />
          통합 채널
        </RouterLink>
        <RouterLink class="sidebar-nav-item sidebar-nav-notification-settings" to="/notification-settings">
          <BellIcon class="nav-icon" aria-hidden="true" />
          알림 설정
        </RouterLink>
        <RouterLink class="sidebar-nav-item sidebar-nav-user-management" to="/user-management">
          <UserGroupIcon class="nav-icon" aria-hidden="true" />
          사용자 관리
        </RouterLink>
        <RouterLink class="sidebar-nav-item sidebar-nav-user-settings" to="/user-settings">
          <Cog6ToothIcon class="nav-icon" aria-hidden="true" />
          설정
        </RouterLink>
      </nav>

      <div class="sidebar-user">
        <div class="avatar">
          <UserCircleIcon aria-hidden="true" />
        </div>
        <div>
          <strong>{{ currentUser.name }}</strong>
          <span>{{ currentUser.email }}</span>
        </div>
        <button class="sidebar-logout" type="button" aria-label="로그아웃" title="로그아웃" @click="handleLogout">
          <ArrowRightStartOnRectangleIcon aria-hidden="true" />
        </button>
      </div>
    </aside>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
