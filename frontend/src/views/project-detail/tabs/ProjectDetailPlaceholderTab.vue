<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <section class="panel detail-card detail-placeholder-card">
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
  </section>
</template>
