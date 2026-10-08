// Service worker volontairement minimal : il ne met rien en cache (le site reste toujours à jour).
self.addEventListener("install", function () { self.skipWaiting(); });
self.addEventListener("activate", function (e) { e.waitUntil(self.clients.claim()); });
self.addEventListener("fetch", function () {});
