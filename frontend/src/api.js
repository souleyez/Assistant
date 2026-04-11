function resolveApiBase() {
  if (import.meta.env.VITE_API_BASE) {
    return import.meta.env.VITE_API_BASE
  }

  if (typeof window !== 'undefined' && window.location?.hostname) {
    return `${window.location.protocol}//${window.location.hostname}:8080`
  }

  return 'http://127.0.0.1:8080'
}

const API_BASE = resolveApiBase()

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
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
