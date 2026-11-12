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
    window.surflogDiagText = function () {
        var meta = document.querySelector("meta[name=app-version]");
        var ua = navigator.userAgent;
        var ios = /OS (\d+[_\d]*) like Mac OS X/.exec(ua);
        var canvases = document.querySelectorAll("canvas");
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
    window.__diagShow = function (title) {
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
        box.appendChild(h); box.appendChild(pre); box.appendChild(copy); box.appendChild(reload); box.appendChild(close);
        document.body.appendChild(box);
    };
})();
