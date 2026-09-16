import { mount } from 'svelte'
import './index.css'
import App from './App.svelte'

function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {}
}

const nativeFetch = window.fetch.bind(window)
window.fetch = (input: RequestInfo | URL, init?: RequestInit) => {
  const method = (init?.method || 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    init = {
      ...init,
      credentials: init?.credentials ?? 'same-origin',
      headers: { ...csrfHeader(), ...(init?.headers as Record<string, string> | undefined) },
    }
  }
  return nativeFetch(input, init)
}

const app = mount(App, {
  target: document.getElementById('app')!,
})

export default app
