// What the app remembers on this device. Wrapped because storage can be blocked (private windows).
const get = (k: string) => {
  try {
    return localStorage.getItem(k)
  } catch {
    return null
  }
}
const set = (k: string, v: string | null) => {
  try {
    if (v === null) localStorage.removeItem(k)
    else localStorage.setItem(k, v)
  } catch {
    /* the choice just is not remembered */
  }
}

export const config = {
  get clientId() {
    return get('omni.clientId') ?? (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined) ?? null
  },
  set clientId(v: string | null) {
    set('omni.clientId', v)
  },
  get vaultId() {
    return get('omni.vaultId')
  },
  set vaultId(v: string | null) {
    set('omni.vaultId', v)
  },
}
