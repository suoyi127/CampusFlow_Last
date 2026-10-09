<script setup lang="ts">
import type { ReviewSummary } from '../reviewTypes'
defineProps<{ summary: ReviewSummary }>()
</script>

<template>
  <div class="review-summary" v-if="summary.count"><span>环境 <strong>{{ summary.environmentAverage?.toFixed(1) }}</strong> / 5</span><span>设施 <strong>{{ summary.facilityAverage?.toFixed(1) }}</strong> / 5</span><span class="muted">{{ summary.count }} 条已通过评价</span></div>
  <p v-else class="muted">暂无已通过评价 · 0 条</p>
</template>
