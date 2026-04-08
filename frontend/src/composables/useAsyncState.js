import { ref } from 'vue'

export function useAsyncState(initialValue) {
  const data = ref(initialValue)
  const loading = ref(false)
  const error = ref('')

  async function run(loader) {
    loading.value = true
    error.value = ''
    try {
      data.value = await loader()
      return data.value
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Request failed'
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    data,
    loading,
    error,
    run,
  }
}
