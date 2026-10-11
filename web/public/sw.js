// Lets the app open without a network: the site's own files (the page, its scripts and styles) are fetched from the
// network as usual and a copy is kept; when the network is not there, the kept copy is served. Network first, so an
// update shows as soon as there is a connection. Google's services are never touched here: the app keeps its notes
// itself (src/offline.ts, src/noteIndex.ts) and says when it is offline.
const CACHE = 'omni-shell-v1'

self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) if (key !== CACHE) await caches.delete(key)
    await self.clients.claim()
  })())
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return
  event.respondWith((async () => {
    const cache = await caches.open(CACHE)
    try {
      const res = await fetch(req)
      if (res.ok) await cache.put(req, res.clone())
      return res
    } catch (e) {
      const hit = await cache.match(req, { ignoreSearch: req.mode === 'navigate' })
      if (hit) return hit
      // Any page of the app opens the app.
      if (req.mode === 'navigate') {
        const page = await cache.match(self.registration.scope)
        if (page) return page
      }
      throw e
    }
  })())
})
