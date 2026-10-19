// Google Calendar for the Omni Task tab (web/src/host.ts, GoogleLink). The owner signs in once in the browser; Google
// sends the browser to a page of the Omni website (web/public/obsidian.html), which hands the answer back to Obsidian
// (obsidian://omni-task-auth). The plugin trades it for a lasting permission (a refresh token) with the OAuth client's
// secret, which the owner pasted in Settings, and from then on renews the hour-long permission by itself. Reading and
// adding events are asked at once, so one sign-in is enough.
import { DriveError, Offline } from '../../web/src/drive'
import type { GoogleLink, GoogleState } from '../../web/src/host'

const AUTHORIZE = 'https://accounts.google.com/o/oauth2/v2/auth'
const TOKEN = 'https://oauth2.googleapis.com/token'
const REVOKE = 'https://oauth2.googleapis.com/revoke'
export const SCOPES = ['https://www.googleapis.com/auth/calendar.readonly', 'https://www.googleapis.com/auth/calendar.events']
/** The protocol action Obsidian hands back to the plugin (obsidian://omni-task-auth?code=...). */
export const AUTH_ACTION = 'omni-task-auth'
/** How long a sign-in started in the browser is waited for. */
const PENDING_MS = 30 * 60_000

/** What the plugin keeps (in its settings file on this device). */
export interface GoogleStore {
  secret?: string
  refreshToken?: string
  email?: string
  /** A sign-in started in the browser: its check and its proof (PKCE), kept in case Obsidian is closed meanwhile. */
  pending?: { state: string; verifier: string; at: number }
  /** Google turned the permission down since it was given. */
  revoked?: boolean
}

export interface HttpRequest {
  url: string
  method: 'GET' | 'POST'
  headers?: Record<string, string>
  contentType?: string
  body?: string
}

export interface GoogleDeps {
  clientId: string
  /** The page of the Omni website Google sends the browser back to (registered for the OAuth client). */
  redirectUri: string
  /** The vault's name, so the answer comes back to this vault's window. */
  vaultName: string
  load(): GoogleStore
  save(store: GoogleStore): Promise<void>
  /** A request that ignores the browser's cross-site rules (Obsidian's requestUrl); throws only without a network. */
  http(req: HttpRequest): Promise<{ status: number; text: string }>
  /** Opens [url] in the system's browser. */
  open(url: string): void
  now?: () => number
  random?: () => string
}

const b64url = (bytes: Uint8Array) => {
  let s = ''
  bytes.forEach((b) => { s += String.fromCharCode(b) })
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const randomString = () => {
  const bytes = new Uint8Array(32)
  crypto.getRandomValues(bytes)
  return b64url(bytes)
}

/** The PKCE challenge for [verifier] (S256), or the verifier itself where the device cannot hash (plain). */
async function challengeOf(verifier: string): Promise<{ challenge: string; method: 'S256' | 'plain' }> {
  try {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
    return { challenge: b64url(new Uint8Array(digest)), method: 'S256' }
  } catch {
    return { challenge: verifier, method: 'plain' }
  }
}

const form = (fields: Record<string, string>) => new URLSearchParams(fields).toString()

const parse = (text: string): any => {
  try {
    return JSON.parse(text)
  } catch {
    return {}
  }
}

export class Google implements GoogleLink {
  private access: { token: string; until: number } | null = null
  private refreshing: Promise<string> | null = null
  private waiting = false
  private lastError: string | null = null
  private listeners = new Set<() => void>()
  private now: () => number
  private random: () => string

  constructor(private deps: GoogleDeps) {
    this.now = deps.now ?? Date.now
    this.random = deps.random ?? randomString
  }

  private get store(): GoogleStore {
    return this.deps.load()
  }

  private async keep(change: Partial<GoogleStore>) {
    await this.deps.save({ ...this.store, ...change })
  }

  private tell() {
    this.listeners.forEach((fn) => fn())
  }

  state(): GoogleState {
    const s = this.store
    if (!s.secret) return 'nosecret'
    if (this.waiting) return 'waiting'
    if (s.refreshToken) return 'on'
    return s.revoked ? 'revoked' : 'off'
  }

  email(): string | null {
    return this.store.email ?? null
  }

  error(): string | null {
    return this.lastError
  }

  /** The store was replaced from outside (another device's link synced in): the short permission is asked anew. */
  reloaded(): void {
    this.access = null
    this.tell()
  }

  listen(fn: () => void): () => void {
    this.listeners.add(fn)
    return () => { this.listeners.delete(fn) }
  }

  async setSecret(secret: string): Promise<void> {
    this.lastError = null
    await this.keep({ secret: secret.trim() })
    this.tell()
  }

  connect(): void {
    if (!this.store.secret) return
    // Waiting at once, so a second tap does not open a second sign-in.
    this.lastError = null
    this.waiting = true
    this.tell()
    void this.begin()
  }

  private async begin() {
    const state = `${this.random().slice(0, 24)}~${this.deps.vaultName}`
    const verifier = this.random() + this.random()
    const { challenge, method } = await challengeOf(verifier)
    await this.keep({ pending: { state, verifier, at: this.now() } })
    const params = new URLSearchParams({
      client_id: this.deps.clientId,
      redirect_uri: this.deps.redirectUri,
      response_type: 'code',
      scope: SCOPES.join(' '),
      // A lasting permission, asked for again each time so Google always hands one over.
      access_type: 'offline',
      prompt: 'consent',
      state,
      code_challenge: challenge,
      code_challenge_method: method,
    })
    this.deps.open(`${AUTHORIZE}?${params}`)
  }

  cancel(): void {
    this.waiting = false
    void this.keep({ pending: undefined })
    this.tell()
  }

  /**
   * The browser came back (obsidian://omni-task-auth): Google's answer for the sign-in begun here. Answers whether
   * the link is made.
   */
  async finish(params: { code?: string; state?: string; error?: string }): Promise<boolean> {
    const pending = this.store.pending
    // Not a sign-in this device began (or one from long ago): left alone. Only the random part before "~" is compared,
    // since the vault's name after it may come back written another way.
    const nonce = (state: string) => state.split('~')[0]
    if (!pending || !params.state || nonce(params.state) !== nonce(pending.state) || this.now() - pending.at > PENDING_MS) return false
    await this.keep({ pending: undefined })
    try {
      if (params.error || !params.code) {
        this.lastError = params.error === 'access_denied' ? 'ยังไม่ได้อนุญาตในหน้าของ Google ลองอีกครั้ง' : `เข้าสู่ระบบไม่สำเร็จ (${params.error ?? 'ไม่มีรหัสจาก Google'})`
        return false
      }
      const res = await this.deps.http({
        url: TOKEN, method: 'POST', contentType: 'application/x-www-form-urlencoded',
        body: form({
          code: params.code, client_id: this.deps.clientId, client_secret: this.store.secret ?? '', redirect_uri: this.deps.redirectUri,
          grant_type: 'authorization_code', code_verifier: pending.verifier,
        }),
      })
      const body = parse(res.text)
      if (res.status !== 200) {
        if (body.error === 'invalid_client' || body.error === 'unauthorized_client') {
          // The secret pasted is not this client's: asked for again.
          await this.keep({ secret: undefined })
          this.lastError = 'Google ไม่รับรหัสลับนี้ ตรวจว่าเป็น Client secret ของ OAuth client แบบเว็บของ Omni แล้ววางใหม่'
        } else {
          this.lastError = `Google ไม่ให้สิทธิ์ (${body.error_description ?? body.error ?? res.status})`
        }
        return false
      }
      if (!body.refresh_token) {
        this.lastError = 'Google ไม่ได้ให้สิทธิ์แบบยาว ถอนสิทธิ์ของ Omni ที่ myaccount.google.com/permissions แล้วเชื่อมใหม่'
        return false
      }
      this.access = { token: body.access_token, until: this.now() + (body.expires_in ?? 3600) * 1000 }
      await this.keep({ refreshToken: body.refresh_token, revoked: false })
      // The account linked, by its primary calendar (its id is the address).
      const primary = await this.call('https://www.googleapis.com/calendar/v3/calendars/primary').catch(() => null)
      await this.keep({ email: typeof primary?.id === 'string' ? primary.id : undefined })
      this.lastError = null
      return true
    } catch (e) {
      this.lastError = e instanceof Offline ? 'ต่อเน็ตไม่ได้ ลองอีกครั้ง' : `เข้าสู่ระบบไม่สำเร็จ (${(e as Error).message})`
      return false
    } finally {
      this.waiting = false
      this.tell()
    }
  }

  async disconnect(): Promise<void> {
    const token = this.store.refreshToken
    this.access = null
    this.waiting = false
    await this.keep({ refreshToken: undefined, email: undefined, revoked: false, pending: undefined })
    this.tell()
    // Asks Google to withdraw it too; the link is gone here whatever Google answers.
    if (token) await this.deps.http({ url: REVOKE, method: 'POST', contentType: 'application/x-www-form-urlencoded', body: form({ token }) }).catch(() => undefined)
  }

  /** An hour-long permission, renewed with the lasting one when it is about to run out. */
  private token(force = false): Promise<string> {
    if (!force && this.access && this.now() < this.access.until - 60_000) return Promise.resolve(this.access.token)
    return (this.refreshing ??= this.renew().finally(() => { this.refreshing = null }))
  }

  private async renew(): Promise<string> {
    const s = this.store
    if (!s.refreshToken || !s.secret) throw new DriveError(401, 'Google Calendar is not linked')
    const res = await this.deps.http({
      url: TOKEN, method: 'POST', contentType: 'application/x-www-form-urlencoded',
      body: form({ client_id: this.deps.clientId, client_secret: s.secret, refresh_token: s.refreshToken, grant_type: 'refresh_token' }),
    })
    const body = parse(res.text)
    if (res.status !== 200) {
      // Withdrawn by the owner, or let lapse by Google: linked again only by signing in.
      if (body.error === 'invalid_grant') await this.lose()
      throw new DriveError(401, `Google ${res.status}: ${body.error ?? ''}`)
    }
    this.access = { token: body.access_token, until: this.now() + (body.expires_in ?? 3600) * 1000 }
    return body.access_token
  }

  private async lose() {
    this.access = null
    await this.keep({ refreshToken: undefined, revoked: true })
    this.tell()
  }

  async call(url: string, body?: unknown): Promise<any> {
    const send = async (token: string) =>
      this.deps.http({
        url, method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}` },
        ...(body === undefined ? {} : { contentType: 'application/json', body: JSON.stringify(body) }),
      })
    let res = await send(await this.token())
    // The hour-long permission ran out early: renewed once.
    if (res.status === 401) {
      res = await send(await this.token(true))
      if (res.status === 401) {
        await this.lose()
        throw new DriveError(401, 'Google turned the permission down')
      }
    }
    if (res.status >= 400) throw new DriveError(res.status, `Google ${res.status}: ${res.text.slice(0, 300)}`)
    return parse(res.text)
  }
}
