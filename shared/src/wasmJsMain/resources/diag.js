// Diagnostic Surf Log : récolte ce qu'il faut pour comprendre une page blanche (navigateur, WebAssembly GC, WebGL, erreurs).
// Chargé en tête de index.html (pour attraper les erreurs dès le début) et par diag.html. Rien n'est envoyé : le texte est affiché et copiable.
(function () {
    var errors = [];
    window.__diagErrors = errors;
    function push(kind, msg) {
        if (errors.length < 30) errors.push(((performance.now() / 1000).toFixed(1)) + "s " + kind + " : " + String(msg).slice(0, 300));
    }
    window.addEventListener("error", function (e) {
        var t = e && e.target;
        if (t && t !== window && (t.src || t.href)) push("ressource", (t.tagName || "") + " " + (t.src || t.href));
        else push("erreur", ((e && e.message) || "?") + (e && e.filename ? " (" + String(e.filename).split("/").pop() + ":" + e.lineno + ")" : ""));
    }, true);
    window.addEventListener("unhandledrejection", function (e) {
        var r = e && e.reason;
        push("promesse", r && (r.stack || r.message) || r);
    });
    // L'appli Kotlin rattrape ses exceptions et les écrit dans la console (console.error) : sans ça, le diagnostic disait
    // « Erreurs (0) » alors qu'une exception venait de partir.
    if (window.console && console.error) {
        var origError = console.error;
        console.error = function () {
            try {
                var parts = [];
                for (var i = 0; i < arguments.length; i++) { var a = arguments[i]; parts.push(a && (a.stack || a.message) || String(a)); }
                push("console", parts.join(" "));
            } catch (e) {}
            return origError.apply(console, arguments);
        };
    }
    document.addEventListener("webglcontextlost", function () { push("webgl", "contexte perdu"); window.__diagShow && window.__diagShow("Le rendu graphique (WebGL) a été perdu."); }, true);
    document.addEventListener("webglcontextcreationerror", function (e) { push("webgl", "création impossible " + ((e && e.statusMessage) || "")); window.__diagShow && window.__diagShow("Le rendu graphique (WebGL) n'a pas pu démarrer."); }, true);

    function webgl(name) {
        try {
            var c = document.createElement("canvas");
            var gl = c.getContext(name);
            if (!gl) return name + " : indisponible";
            var info = gl.getExtension("WEBGL_debug_renderer_info");
            var r = info ? gl.getParameter(info.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
            return name + " : oui, " + r + ", texture max " + gl.getParameter(gl.MAX_TEXTURE_SIZE) + (gl.isContextLost() ? ", PERDU" : "");
        } catch (e) { return name + " : erreur " + e; }
    }
    function wasmGc() {
        try { return typeof WebAssembly !== "undefined" && WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 120, 0])); } catch (e) { return false; }
    }
    // Le canvas de l'appli est dans un shadow DOM : document.querySelector ne le voit pas.
    window.__findCanvas = function () {
        function walk(node, depth) {
            var kids = node.children || [];
            for (var i = 0; i < kids.length; i++) {
                var k = kids[i];
                if (k.tagName === "CANVAS") return k;
                var c = (k.shadowRoot && walk(k.shadowRoot, depth + 1)) || (depth < 5 && walk(k, depth + 1));
                if (c) return c;
            }
            return null;
        }
        return document.body ? walk(document.body, 0) : null;
    };
    // Échantillon de l'écran dessiné : null si illisible, sinon {uniform, color} (uniform = tous les pixels pareils : écran vide).
    window.__sampleCanvas = function () {
        var c = window.__findCanvas();
        if (!c || !c.width || !c.height) return null;
        try {
            var t = document.createElement("canvas"); t.width = t.height = 24;
            var x = t.getContext("2d", { willReadFrequently: true });
            x.drawImage(c, 0, 0, 24, 24);
            var d = x.getImageData(0, 0, 24, 24).data, u = true;
            for (var i = 4; i < d.length; i += 4) if (d[i] !== d[0] || d[i + 1] !== d[1] || d[i + 2] !== d[2] || d[i + 3] !== d[3]) { u = false; break; }
            return { uniform: u, color: d[0] + "," + d[1] + "," + d[2] + "," + d[3] };
        } catch (e) { return null; }
    };
    // Le tampon WebGL est vidé après l'affichage : on ne peut le lire qu'au moment où l'appli vient de dessiner. On enveloppe
    // requestAnimationFrame pour échantillonner juste après sa fonction de dessin (au plus une fois toutes les 2 s).
    window.__samples = [];
    var lastSample = 0, origRaf = window.requestAnimationFrame;
    if (origRaf) {
        window.requestAnimationFrame = function (cb) {
            return origRaf.call(window, function (t) {
                cb(t);
                var now = Date.now();
                if (now - lastSample > 2000 && window.__findCanvas()) {
                    lastSample = now;
                    var s = window.__sampleCanvas();
                    if (s) { window.__samples.push(s); if (window.__samples.length > 40) window.__samples.shift(); }
                }
            });
        };
    }
    window.__diagPush = push;
    window.surflogDiagText = function () {
        var meta = document.querySelector("meta[name=app-version]");
        var ua = navigator.userAgent;
        var ios = /OS (\d+[_\d]*) like Mac OS X/.exec(ua);
        var fc = window.__findCanvas(), canvases = fc ? [fc] : [];
        var sizes = [];
        for (var i = 0; i < canvases.length; i++) sizes.push(canvases[i].width + "x" + canvases[i].height);
        var lines = [
            "Surf Log " + (meta ? meta.getAttribute("content") : "?") + " — diagnostic",
            "Navigateur : " + ua,
            "iOS : " + (ios ? ios[1].replace(/_/g, ".") : "n/a"),
            "Écran : " + screen.width + "x" + screen.height + " @" + window.devicePixelRatio + ", fenêtre " + innerWidth + "x" + innerHeight,
            "WebAssembly : " + (typeof WebAssembly !== "undefined" ? "oui" : "NON") + ", GC : " + (wasmGc() ? "oui" : "NON"),
            webgl("webgl2"),
            webgl("webgl"),
            "Canvas dans la page : " + canvases.length + (sizes.length ? " (" + sizes.join(", ") + ")" : "") + ", #loading " + (document.getElementById("loading") ? "présent" : "retiré"),
            "Réseau : " + (navigator.connection ? navigator.connection.effectiveType : "n/a"),
            "Erreurs (" + errors.length + ") :"
        ];
        return lines.concat(errors.length ? errors : ["aucune"]).join("\n");
    };
    // Affiche un panneau par-dessus la page avec le texte du diagnostic et un bouton pour le copier.
    window.__diagShow = function (title, opts) {
        if (document.getElementById("surflog-diag")) return;
        var box = document.createElement("div");
        box.id = "surflog-diag";
        box.style.cssText = "position:fixed;inset:0;z-index:2147483647;background:#0D1B22;color:#dce6eb;padding:16px;overflow:auto;font:13px -apple-system,system-ui,sans-serif";
        var h = document.createElement("div");
        h.style.cssText = "font-weight:700;font-size:16px;margin-bottom:8px";
        h.textContent = title || "Diagnostic";
        var pre = document.createElement("pre");
        pre.style.cssText = "white-space:pre-wrap;word-break:break-word;font:11px ui-monospace,monospace;background:#16283a;padding:10px;border-radius:8px";
        pre.textContent = window.surflogDiagText();
        var copy = document.createElement("button");
        copy.textContent = "Copier le diagnostic";
        copy.style.cssText = "background:#1E88E5;color:#fff;border:0;border-radius:20px;padding:10px 18px;margin:10px 8px 0 0;font:600 14px -apple-system,system-ui,sans-serif";
        copy.onclick = function () {
            var t = pre.textContent;
            try { navigator.clipboard.writeText(t).then(function () { copy.textContent = "Copié"; }, function () { sel(); }); } catch (e) { sel(); }
            function sel() { var r = document.createRange(); r.selectNodeContents(pre); var s = getSelection(); s.removeAllRanges(); s.addRange(r); copy.textContent = "Texte sélectionné : copie-le"; }
        };
        var reload = document.createElement("button");
        reload.textContent = "Recharger";
        reload.style.cssText = copy.style.cssText.replace("#1E88E5", "#3a4b57");
        reload.onclick = function () { location.reload(); };
        var close = document.createElement("button");
        close.textContent = "Fermer";
        close.style.cssText = reload.style.cssText;
        close.onclick = function () { box.remove(); };
        var status = document.createElement("div");
        status.style.cssText = "margin-top:10px;font-size:12px;opacity:.8";
        // Envoi automatique au développeur (relais Cloudflare /api/report) : texte technique seulement, une fois par panneau,
        // pas plus d'un envoi toutes les 10 minutes par navigateur, jamais en local ni depuis diag.html.
        var canSend = !(opts && opts.noSend) && /(^|\.)surflog\.fr$/.test(location.hostname);
        if (canSend) {
            var recent = false;
            try { recent = Date.now() - Number(localStorage.getItem("surflog_report_at") || 0) < 600000; } catch (e) {}
            if (recent) status.textContent = "Rapport déjà envoyé au développeur.";
            else {
                status.textContent = "Envoi du rapport technique au développeur (navigateur et erreurs, rien de personnel)…";
                try { localStorage.setItem("surflog_report_at", String(Date.now())); } catch (e) {}
                var meta = document.querySelector("meta[name=app-version]");
                fetch("/api/report", { method: "POST", keepalive: true, headers: { "content-type": "text/plain" },
                    body: JSON.stringify({ v: meta ? meta.getAttribute("content") : "", title: title || "", text: pre.textContent }) })
                    .then(function (r) { status.textContent = r.ok ? "Rapport envoyé au développeur. Merci !" : "Le rapport n'a pas pu être envoyé (" + r.status + ")."; },
                          function () { status.textContent = "Le rapport n'a pas pu être envoyé (pas de réseau ?). Tu peux copier le diagnostic."; });
            }
        }
        box.appendChild(h); box.appendChild(pre); if (canSend) box.appendChild(status); box.appendChild(copy); box.appendChild(reload); box.appendChild(close);
        // L'appli vide le body en démarrant (le panneau disparaissait, d'où un fond sombre vide) : on l'accroche à <html>
        // et on le remet s'il est retiré, tant qu'on ne l'a pas fermé.
        var closed = false;
        close.onclick = function () { closed = true; box.remove(); };
        document.documentElement.appendChild(box);
        new MutationObserver(function () { if (!closed && !box.isConnected) document.documentElement.appendChild(box); })
            .observe(document.documentElement, { childList: true });
    };
})();
