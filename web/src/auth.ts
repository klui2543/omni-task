// Google sign-in with the token flow of Google Identity Services: the browser gets a short-lived access token
// for the owner's Drive and the app talks to Drive directly. Nothing passes through a server of ours.

const SCRIPT = 'https://accounts.google.com/gsi/client'
export const SCOPE = 'https://www.googleapis.com/auth/drive'

interface TokenResponse {
  access_token?: string
  expires_in?: number
  error?: string
}

declare const google: {
  accounts: {
    oauth2: {
      initTokenClient(c: {
        client_id: string
        scope: string
        callback: (r: TokenResponse) => void
        error_callback?: (e: { type: string }) => void
      }): { requestAccessToken(o?: { prompt?: string }): void }
      revoke(token: string, done?: () => void): void
    }
  }
}

let loading: Promise<void> | null = null
function loadScript(): Promise<void> {
  loading ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = SCRIPT
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => reject(new Error('โหลดตัวล็อกอินของ Google ไม่ได้ ตรวจสอบอินเทอร์เน็ต'))
    document.head.appendChild(s)
  })
  return loading
}

export class Auth {
  private token: string | null = null
  private expiresAt = 0

  constructor(private clientId: string) {}

  get signedIn() {
    return this.token !== null && Date.now() < this.expiresAt - 30_000
  }

  /** The access token; asks Google again when it has run out. [prompt] '' signs in without a dialog when allowed. */
  async getToken(prompt: '' | 'consent' | 'select_account' = ''): Promise<string> {
    if (this.signedIn) return this.token!
    await loadScript()
    return new Promise((resolve, reject) => {
      const client = google.accounts.oauth2.initTokenClient({
        client_id: this.clientId,
        scope: SCOPE,
        callback: (r) => {
          if (r.access_token) {
            this.token = r.access_token
            this.expiresAt = Date.now() + (r.expires_in ?? 3600) * 1000
            resolve(r.access_token)
          } else {
            reject(new Error(r.error ?? 'sign-in failed'))
          }
        },
        error_callback: (e) => reject(new Error(e.type)),
      })
      client.requestAccessToken({ prompt })
    })
  }

  signOut() {
    if (this.token) google.accounts.oauth2.revoke(this.token)
    this.token = null
    this.expiresAt = 0
  }
}
