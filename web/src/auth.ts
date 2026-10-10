// Google sign-in by redirect (OAuth 2.0 for browser apps): the page goes to Google and comes back with a
// short-lived access token in the address. A redirect, not a popup, because popups are often blocked in an app
// added to the iPad home screen. The token is kept on this device until it runs out (an hour), so opening the
// app again does not ask again; after that one quiet round trip to Google renews it. The sign-in as a whole
// lasts 24 hours from the owner's last sign-in; then the sign-in page asks again.

const AUTHORIZE = 'https://accounts.google.com/o/oauth2/v2/auth'
const REVOKE = 'https://oauth2.googleapis.com/revoke'
export const SCOPE = 'https://www.googleapis.com/auth/drive'
/** Asked for only when the owner connects Google Calendar on the Focus page. */
export const CALENDAR_SCOPE = 'https://www.googleapis.com/auth/calendar.readonly'
/**
 * Asked for only when the owner first lets the assistant put an event on the calendar, never with sign-in. It can
 * create events but not list the calendars, so it is asked together with the read one.
 */
export const CALENDAR_WRITE_SCOPE = 'https://www.googleapis.com/auth/calendar.events'

const TOKEN_KEY = 'omni.token'
const STATE_KEY = 'omni.oauthState'
const SILENT_KEY = 'omni.silentTried'
const RETURN_KEY = 'omni.returnTo'
const SIGNED_IN_BEFORE = 'omni.signedInBefore'
const SCOPES_KEY = 'omni.scopes'
const LOGIN_AT_KEY = 'omni.loginAt'
const ENDED_KEY = 'omni.sessionEnded'

/** A sign-in lasts this long from the owner's last sign-in; quiet renewals in between do not extend it. */
export const SESSION_MS = 24 * 60 * 60 * 1000

interface Stored {
  token: string
  expiresAt: number
}

const store = {
  get: (s: Storage, k: string) => {
    try {
      return s.getItem(k)
    } catch {
      return null
    }
  },
  set: (s: Storage, k: string, v: string | null) => {
    try {
      if (v === null) s.removeItem(k)
      else s.setItem(k, v)
    } catch {
      /* not remembered; the next launch signs in again */
    }
  },
}

/** The address Google sends the owner back to; it must be listed as an authorized redirect URI. */
export const redirectUri = () => location.origin + location.pathname.replace(/index\.html$/, '')

export class Auth {
  constructor(private clientId: string) {
    // Signed in before this limit existed: it counts from now. Past the limit, the session is over.
    if (store.get(localStorage, SIGNED_IN_BEFORE) === '1' && store.get(localStorage, LOGIN_AT_KEY) === null) store.set(localStorage, LOGIN_AT_KEY, String(Date.now()))
    this.endIfOld()
  }

  /** Ends the sign-in when the owner last signed in [SESSION_MS] ago or more: the next visit asks for a sign-in. */
  private endIfOld(): boolean {
    const at = Number(store.get(localStorage, LOGIN_AT_KEY))
    if (!at || Date.now() - at < SESSION_MS) return false
    this.forget()
    store.set(localStorage, ENDED_KEY, '1')
    return true
  }

  private forget() {
    store.set(localStorage, TOKEN_KEY, null)
    store.set(localStorage, SIGNED_IN_BEFORE, null)
    store.set(localStorage, SCOPES_KEY, null)
    store.set(localStorage, LOGIN_AT_KEY, null)
  }

  /** Whether the last sign-in ended by itself after 24 hours (the sign-in page says so). */
  static get sessionEnded(): boolean {
    return store.get(localStorage, ENDED_KEY) === '1'
  }

  /**
   * Picks up what Google just sent back in the address, if anything, and clears it from the address bar.
   * Returns Google's error when sign-in did not happen.
   */
  static consumeRedirect(): string | null {
    if (!location.hash.includes('state=')) return null
    const p = new URLSearchParams(location.hash.slice(1))
    // Back to the page the owner left (the hash is the page, as #/assistant), not the first one.
    const back = store.get(sessionStorage, RETURN_KEY) ?? ''
    store.set(sessionStorage, RETURN_KEY, null)
    history.replaceState(null, '', location.pathname + location.search + (/^#\/\w+$/.test(back) ? back : ''))
    const expected = store.get(sessionStorage, STATE_KEY)
    store.set(sessionStorage, STATE_KEY, null)
    if (!expected || p.get('state') !== expected) return 'state_mismatch'
    const token = p.get('access_token')
    if (!token) return p.get('error') ?? 'no_token'
    // A quiet renewal keeps the time of the last sign-in the owner made; any other trip to Google is a sign-in.
    if (store.get(sessionStorage, SILENT_KEY) !== '1' || store.get(localStorage, LOGIN_AT_KEY) === null) store.set(localStorage, LOGIN_AT_KEY, String(Date.now()))
    store.set(localStorage, ENDED_KEY, null)
    const stored: Stored = { token, expiresAt: Date.now() + Number(p.get('expires_in') ?? 3600) * 1000 }
    store.set(localStorage, TOKEN_KEY, JSON.stringify(stored))
    store.set(localStorage, SIGNED_IN_BEFORE, '1')
    // What Google says the token covers; a renewal keeps what was granted before.
    if (p.get('scope')) store.set(localStorage, SCOPES_KEY, p.get('scope'))
    store.set(sessionStorage, SILENT_KEY, null)
    return null
  }

  /** The access token while it is still good for a minute or more, else null. */
  get token(): string | null {
    if (this.endIfOld()) return null
    const raw = store.get(localStorage, TOKEN_KEY)
    if (!raw) return null
    try {
      const s: Stored = JSON.parse(raw)
      return Date.now() < s.expiresAt - 60_000 ? s.token : null
    } catch {
      return null
    }
  }

  /**
   * Whether to renew quietly now: signed in on this device before, the token has run out, and no quiet try has
   * failed in this visit (Google answers a quiet try it cannot serve with an error, which must not loop).
   */
  get shouldRenewSilently() {
    return this.token === null && store.get(localStorage, SIGNED_IN_BEFORE) === '1' && store.get(sessionStorage, SILENT_KEY) !== '1'
  }

  /** Whether the owner has agreed to [scope] on this device. */
  static granted(scope: string): boolean {
    return (store.get(localStorage, SCOPES_KEY) ?? '').split(/[\s,]+/).includes(scope)
  }

  /**
   * Leaves the page for Google. [prompt] 'none' renews without showing anything when Google allows it.
   * [extra] asks for one more permission on top of what was granted (Google Calendar, from the Focus page).
   */
  signIn(prompt: 'none' | 'select_account' = 'select_account', extra?: string) {
    const state = crypto.randomUUID()
    store.set(sessionStorage, STATE_KEY, state)
    store.set(sessionStorage, RETURN_KEY, location.hash)
    if (prompt === 'none') store.set(sessionStorage, SILENT_KEY, '1')
    const params = new URLSearchParams({
      client_id: this.clientId,
      redirect_uri: redirectUri(),
      response_type: 'token',
      scope: extra ? `${SCOPE} ${extra}` : SCOPE,
      include_granted_scopes: 'true',
      state,
      prompt,
    })
    location.assign(`${AUTHORIZE}?${params}`)
  }

  signOut() {
    const token = this.token
    if (token) fetch(`${REVOKE}?token=${encodeURIComponent(token)}`, { method: 'POST' }).catch(() => {})
    this.forget()
    store.set(localStorage, ENDED_KEY, null)
  }
}
