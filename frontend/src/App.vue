<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isAuthLayout = computed(() => route.meta.layout === 'auth')
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
      </div>
    </aside>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
