function resolveApiBase() {
  if (import.meta.env.VITE_API_BASE) {
    return import.meta.env.VITE_API_BASE
  }

  if (typeof window !== 'undefined' && window.location?.origin) {
    return `${window.location.origin}/assistant-api`
  }

  return 'http://127.0.0.1/assistant-api'
}

const API_BASE = resolveApiBase()

async function request(path, options = {}) {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData
  const headers = {
    ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
    ...(options.headers || {}),
  }

  const response = await fetch(`${API_BASE}${path}`, {
    headers,
    ...options,
  })

  if (!response.ok) {
    let message = `Request failed: ${response.status}`
    try {
      const json = await response.json()
      message = json.message || json.error || message
    } catch {
      // ignore parse failure
    }
    throw new Error(message)
  }

  if (response.status === 204) {
    return null
  }

  return response.json()
}

export { API_BASE, request }
