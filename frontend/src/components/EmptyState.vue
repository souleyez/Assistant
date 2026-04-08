<template>
  <section class="panel empty-state">
    <h3>{{ title }}</h3>
    <p>{{ text }}</p>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  text: { type: String, required: true },
})
</script>
