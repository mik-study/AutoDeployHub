<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isAuthLayout = computed(() => route.meta.layout === 'auth')

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <RouterView v-if="isAuthLayout" />

  <div v-else class="app-layout">
    <aside class="sidebar">
      <RouterLink class="sidebar-brand" to="/projects">
        <span class="brand-mark">AD</span>
        <span>AutoDeploy<span>Hub</span></span>
      </RouterLink>

      <nav class="sidebar-nav" aria-label="Primary">
        <RouterLink to="/projects">
          <span class="nav-icon">P</span>
          프로젝트
        </RouterLink>
      </nav>

      <div class="sidebar-user">
        <div class="avatar">A</div>
        <div>
          <strong>admin</strong>
          <span>Administrator</span>
        </div>
        <button class="sidebar-logout" type="button" @click="handleLogout">로그아웃</button>
      </div>
    </aside>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
