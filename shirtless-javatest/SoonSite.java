// ============================================================
//  SoonSite.java — the "Soon." site served by a pure-Java server
//
//  Run (Java 17+):   java SoonSite.java
//  Then open:        http://localhost:8080
//
//  Put smolheart.mp3, pinkiii.mp3, heartheartheart.mp3 in the
//  same folder as this file so the sounds work.
//
//  Server-side optimizations:
//   - GZIP compression for HTML/CSS (smaller transfers)
//   - Cache-Control headers (browser caches CSS + audio)
//   - Audio streamed from disk with correct MIME types
//   - Virtual-thread executor (Java 21) with a safe fallback
//
//  Front-end optimizations over the previous version:
//   - <template> + cloneNode instead of innerHTML parsing per heart
//   - Pooled burst particles (DOM nodes reused, not recreated)
//   - Single delegated cleanup via animationend (fewer timers)
// ============================================================

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

public class SoonSite {

    static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            switch (path) {
                case "/", "/index.html" -> sendText(ex, INDEX_HTML, "text/html; charset=utf-8", false);
                case "/style.css"       -> sendText(ex, STYLE_CSS, "text/css; charset=utf-8", true);
                default -> {
                    if (path.endsWith(".mp3")) sendAudio(ex, path);
                    else sendNotFound(ex);
                }
            }
        });

        ExecutorService pool;
        try {
            pool = Executors.newVirtualThreadPerTaskExecutor(); // Java 21+
        } catch (Throwable t) {
            pool = Executors.newFixedThreadPool(8);             // fallback
        }
        server.setExecutor(pool);
        server.start();
        System.out.println("♡  Soon. is live at http://localhost:" + PORT);
    }

    // ---------- response helpers ----------

    static void sendText(HttpExchange ex, String body, String mime, boolean cache) throws IOException {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", mime);
        if (cache) ex.getResponseHeaders().set("Cache-Control", "public, max-age=3600");

        String accept = ex.getRequestHeaders().getFirst("Accept-Encoding");
        if (accept != null && accept.contains("gzip")) {
            ex.getResponseHeaders().set("Content-Encoding", "gzip");
            ex.sendResponseHeaders(200, 0);
            try (GZIPOutputStream gz = new GZIPOutputStream(ex.getResponseBody())) {
                gz.write(raw);
            }
        } else {
            ex.sendResponseHeaders(200, raw.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(raw);
            }
        }
    }

    static void sendAudio(HttpExchange ex, String path) throws IOException {
        // Only serve known mp3 names from the working directory — no path traversal.
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (!name.matches("[A-Za-z0-9_-]+\\.mp3")) { sendNotFound(ex); return; }

        Path file = Path.of(name);
        if (!Files.isRegularFile(file)) { sendNotFound(ex); return; }

        ex.getResponseHeaders().set("Content-Type", "audio/mpeg");
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        byte[] data = Files.readAllBytes(file);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    static void sendNotFound(HttpExchange ex) throws IOException {
        byte[] msg = "Not found".getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(404, msg.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(msg);
        }
    }

    // ============================================================
    //  Embedded front-end
    // ============================================================

    static final String INDEX_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Soon. &#9825;</title>
    <link rel="stylesheet" href="style.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Fredoka:wght@400;600&display=swap" rel="stylesheet">
</head>
<body>
    <!-- Ambient floating hearts -->
    <div id="hearts-container"></div>
    <!-- Layer for the easter-egg particle sequence -->
    <div id="secret-animation-layer"></div>

    <div class="center-container" id="main-content">
        <p class="teaser-text" id="text-display">Soon.</p>

        <!-- Mystical crystal lightswitch -->
        <div class="switch-container" id="lightswitch-wrapper">
            <div class="switch-aura"></div>
            <div class="switch-housing">
                <div class="switch-toggle"></div>
            </div>
        </div>
    </div>

    <!-- Hidden mystical heart easter-egg trigger -->
    <div id="secret-rainbow-heart">
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id="secretHeartGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#ffffff"/>
                    <stop offset="45%" stop-color="#c9a7ff"/>
                    <stop offset="100%" stop-color="#ff6ebb"/>
                </linearGradient>
            </defs>
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="url(#secretHeartGradient)"/>
        </svg>
    </div>

    <!-- Template cloned for every falling / bursting heart: parsed once, reused forever -->
    <template id="heart-svg-template">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="#ff6ebb"/>
        </svg>
    </template>

    <script>
        const container = document.getElementById('hearts-container');
        const secretLayer = document.getElementById('secret-animation-layer');
        const secretHeart = document.getElementById('secret-rainbow-heart');
        const textDisplay = document.getElementById('text-display');
        const switchWrapper = document.getElementById('lightswitch-wrapper');
        const heartTemplate = document.getElementById('heart-svg-template');

        let switchClickCount = 0;
        let secretTriggeredThisSession = false;
        let animationMusic = null;
        let targetClicks = Math.floor(Math.random() * 4) + 6;

        // Listeners attached in JS instead of inline onclick attributes
        switchWrapper.addEventListener('click', toggleLight);
        secretHeart.addEventListener('click', triggerSecretAnimation);

        // Reusable audio elements — decoded once, replayed forever
        const clickSound = new Audio('smolheart.mp3');
        const explosionSound = new Audio('pinkiii.mp3');
        clickSound.volume = 0.5;
        explosionSound.volume = 0.5;
        clickSound.preload = 'auto';
        explosionSound.preload = 'auto';

        function playClickSound() {
            clickSound.currentTime = 0;
            clickSound.play().catch(() => {});
        }

        function playExplosionSound() {
            explosionSound.currentTime = 0;
            explosionSound.play().catch(() => {});
        }

        function startFullAnimationMusic() {
            animationMusic = new Audio('heartheartheart.mp3');
            animationMusic.volume = 0.5;
            animationMusic.play().catch(() => {});
        }

        function stopFullAnimationMusic() {
            if (animationMusic) {
                animationMusic.pause();
                animationMusic.currentTime = 0;
            }
        }

        function toggleLight() {
            const isLightMode = document.body.classList.toggle('light-mode');

            if (isLightMode) {
                createNormalHearts(20);
            } else {
                container.replaceChildren(); // faster than innerHTML = ''
            }

            if (!secretTriggeredThisSession) {
                switchClickCount++;
                secretHeart.classList.toggle('visible', switchClickCount === targetClicks);
            }
        }

        function createNormalHearts(count) {
            const frag = document.createDocumentFragment();
            for (let i = 0; i < count; i++) {
                const heart = document.createElement('div');
                heart.className = 'heart';
                heart.textContent = '\\u2661';
                heart.style.cssText =
                    'left:' + (Math.random() * -20) + 'vw;' +
                    'top:' + (Math.random() * 100) + 'vh;' +
                    'font-size:' + (Math.random() * 15 + 20) + 'px;' +
                    'animation-delay:' + (Math.random() * 6) + 's;' +
                    'animation-duration:' + (Math.random() * 4 + 7) + 's;';
                frag.appendChild(heart);
            }
            container.replaceChildren(frag);
        }

        function triggerSecretAnimation() {
            secretTriggeredThisSession = true;
            secretHeart.classList.remove('visible');
            switchClickCount = 0;

            playClickSound();
            startFullAnimationMusic();

            textDisplay.textContent = 'SOON!<3';
            textDisplay.classList.add('cute-mode');
            switchWrapper.style.display = 'none';

            const dropInterval = setInterval(createDroppingPinkHeart, 430);

            setTimeout(() => {
                clearInterval(dropInterval);
                stopFullAnimationMusic();
                document.body.classList.add('scene-fade-out');

                setTimeout(() => {
                    secretLayer.replaceChildren();
                    sparkPool.length = 0;   // pooled nodes were detached above
                    burstHeartPool.length = 0;
                    textDisplay.textContent = 'Soon.';
                    textDisplay.classList.remove('cute-mode');
                    switchWrapper.style.display = 'inline-block';
                    document.body.classList.remove('scene-fade-out');

                    targetClicks = Math.floor(Math.random() * 4) + 6;
                    secretTriggeredThisSession = false;
                }, 1500);

            }, 5500);
        }

        function makeHeartSvgNode() {
            return heartTemplate.content.firstElementChild.cloneNode(true);
        }

        function createDroppingPinkHeart() {
            const targetX = Math.random() * 70 + 15;
            const targetY = Math.random() * 45 + 20;
            const floatDuration = Math.random() * 300 + 1300;

            // Soft blurred trail behind the heart — same CSS animation with
            // a negative delay, zero per-frame JavaScript.
            const trail = document.createElement('div');
            trail.className = 'heart-glow-trail';
            trail.style.left = targetX + 'vw';
            trail.style.setProperty('--target-y', targetY + 'vh');
            trail.style.animationDuration = floatDuration + 'ms';

            const pinkHeart = document.createElement('div');
            pinkHeart.className = 'dropping-pink-heart';
            pinkHeart.style.left = targetX + 'vw';
            pinkHeart.style.setProperty('--target-y', targetY + 'vh');
            pinkHeart.style.animationDuration = floatDuration + 'ms';
            pinkHeart.appendChild(makeHeartSvgNode());

            secretLayer.appendChild(trail);
            secretLayer.appendChild(pinkHeart);

            // animationend fires exactly when the CSS drop finishes —
            // more accurate than a parallel setTimeout, and one less timer.
            pinkHeart.addEventListener('animationend', () => {
                const rect = pinkHeart.getBoundingClientRect();
                trail.remove();
                pinkHeart.remove();
                playExplosionSound();
                createPopBurst(rect.left + rect.width / 2, rect.top + rect.height / 2);
            }, { once: true });
        }

        // ---- pooled burst particles: nodes are parked and reused, not rebuilt ----
        const sparkPool = [];
        const burstHeartPool = [];

        function obtainSpark() {
            const s = sparkPool.pop() || document.createElement('div');
            s.className = 'burst-spark';
            return s;
        }

        function obtainBurstHeart() {
            let h = burstHeartPool.pop();
            if (!h) {
                h = document.createElement('div');
                h.appendChild(makeHeartSvgNode());
            }
            h.className = 'burst-heart';
            return h;
        }

        function recycle(node, pool) {
            node.remove();
            node.className = '';       // stops the animation so it can restart cleanly
            pool.push(node);
        }

        function createPopBurst(startX, startY) {
            const ring = document.createElement('div');
            ring.className = 'burst-pop-ring';
            ring.style.left = startX + 'px';
            ring.style.top = startY + 'px';
            secretLayer.appendChild(ring);
            ring.addEventListener('animationend', () => ring.remove(), { once: true });

            const distance = Math.random() * 30 + 90;
            const rotationOffset = Math.random() * 2 * Math.PI;

            // A handful of soft dot sparks
            const sparkCount = 8;
            for (let i = 0; i < sparkCount; i++) {
                const spark = obtainSpark();

                const angle = (i / sparkCount) * 2 * Math.PI + rotationOffset;
                const dist = distance * (0.8 + Math.random() * 0.4);
                const size = Math.random() * 5 + 5;

                spark.style.left = startX + 'px';
                spark.style.top = startY + 'px';
                spark.style.width = size + 'px';
                spark.style.height = size + 'px';
                spark.style.background = i % 2 === 0 ? '#ff9ecb' : '#ffd6ec';
                spark.style.setProperty('--tx', (Math.cos(angle) * dist) + 'px');
                spark.style.setProperty('--ty', (Math.sin(angle) * dist) + 'px');

                secretLayer.appendChild(spark);
                spark.addEventListener('animationend', () => recycle(spark, sparkPool), { once: true });
            }

            // A few tiny hearts for the cute touch — no emoji glyphs
            const heartCount = 3;
            for (let i = 0; i < heartCount; i++) {
                const heart = obtainBurstHeart();

                const angle = Math.random() * 2 * Math.PI;
                const dist = distance * (0.6 + Math.random() * 0.3);

                heart.style.left = startX + 'px';
                heart.style.top = startY + 'px';
                heart.style.setProperty('--tx', (Math.cos(angle) * dist) + 'px');
                heart.style.setProperty('--ty', (Math.sin(angle) * dist) + 'px');

                secretLayer.appendChild(heart);
                heart.addEventListener('animationend', () => recycle(heart, burstHeartPool), { once: true });
            }
        }
    </script>
</body>
</html>
""";

    static final String STYLE_CSS = """
/* ============================================================
   Token system — soft mystical / preppy palette
   ============================================================ */
:root {
    --rose: #ff6ebb;
    --rose-soft: #ffb6c1;
    --blush: #ffe3ef;
    --lilac: #c9a7ff;
    --cream: #fff7fa;
    --mauve: #8a7290;
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    font-family: 'Fredoka', sans-serif;
}

/* ============================================================
   Base
   ============================================================ */
body {
    background-color: #000000;
    height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
    position: relative;
    transition: background-color .3s ease, opacity 1s ease;
    opacity: 1;
    cursor: default;
}

body.scene-fade-out { opacity: 0; }

/* subtle static stardust — no animation, cheap paint, quiet ambience */
body::before {
    content: "";
    position: absolute;
    inset: 0;
    background-image:
        radial-gradient(1.5px 1.5px at 18% 28%, rgba(255,182,220,.35) 50%, transparent 51%),
        radial-gradient(1.5px 1.5px at 72% 62%, rgba(201,167,255,.3) 50%, transparent 51%),
        radial-gradient(1px 1px at 40% 82%, rgba(255,255,255,.25) 50%, transparent 51%),
        radial-gradient(1.5px 1.5px at 86% 18%, rgba(255,182,220,.3) 50%, transparent 51%);
    opacity: .7;
    pointer-events: none;
}

.center-container {
    display: flex;
    align-items: center;
    gap: 34px;
    position: relative;
    z-index: 999;
    pointer-events: auto;
}

/* ============================================================
   Text
   ============================================================ */
.teaser-text {
    color: #ffffff;
    font-size: 2rem;
    font-weight: 600;
    letter-spacing: 1.5px;
    text-transform: uppercase;
    text-shadow: 0 0 18px rgba(255, 182, 220, .3);
    transition: color .25s ease;
}

.teaser-text.cute-mode {
    font-size: 4.4rem;
    text-transform: none;
    letter-spacing: 0.5px;
    color: var(--rose) !important;
    text-align: center;
    line-height: 1.1;
    text-shadow: 0 0 22px rgba(255,110,187,.7), 0 0 44px rgba(255,182,220,.35);
    animation: gentlePulse 1.1s ease-in-out infinite alternate;
}

@keyframes gentlePulse {
    0%   { transform: scale(1); }
    100% { transform: scale(1.035); }
}

/* ============================================================
   Mystical lightswitch — crystal capsule with glowing core
   ============================================================ */
.switch-container {
    cursor: pointer;
    display: inline-block;
    position: relative;
    z-index: 1000;
    pointer-events: auto;
    transition: transform .25s cubic-bezier(.175,.885,.32,1.275);
}

.switch-container:hover { transform: scale(1.08); }
.switch-container:active { transform: scale(.94); }

.switch-aura {
    position: absolute;
    inset: -10px;
    border-radius: 30px;
    background: radial-gradient(circle, rgba(201,167,255,.25) 0%, transparent 70%);
    filter: blur(4px);
    animation: auraBreathe 3.2s ease-in-out infinite;
    pointer-events: none;
}

@keyframes auraBreathe {
    0%, 100% { opacity: .5; transform: scale(1); }
    50%      { opacity: .9; transform: scale(1.12); }
}

.switch-housing {
    width: 32px;
    height: 52px;
    background: linear-gradient(160deg, #14111f 0%, #1d1830 100%);
    border: 1.5px solid rgba(201, 167, 255, .55);
    border-radius: 25px;
    position: relative;
    padding: 2px;
    box-shadow: 0 0 18px rgba(201, 167, 255, .25), inset 0 0 8px rgba(255,255,255,.06);
    transition: border-color .25s ease, box-shadow .25s ease;
}

.switch-toggle {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    position: absolute;
    top: 4px;
    left: 3px;
    background: #ffffff;
    box-shadow: 0 2px 5px rgba(0,0,0,.3), 0 0 6px rgba(201,167,255,.6);
    transition: top .25s cubic-bezier(.175,.885,.32,1.275), background .25s ease;
}

/* ============================================================
   Ambient layers — GPU-only transforms, no layout thrash
   ============================================================ */
#hearts-container, #secret-animation-layer {
    position: absolute;
    inset: 0;
    pointer-events: none;
    overflow: hidden;
    contain: layout style paint;
}

.heart {
    position: absolute;
    color: transparent;
    -webkit-text-stroke: 1.5px var(--rose-soft);
    user-select: none;
    opacity: 0;
    will-change: transform, opacity;
    animation: drift linear infinite;
}

@keyframes drift {
    0%   { transform: translate3d(0,0,0) rotate(0deg) scale(.8); opacity: 0; }
    10%  { opacity: .7; }
    50%  { transform: translate3d(55vw,10vh,0) rotate(20deg) scale(1.1); }
    90%  { opacity: .7; }
    100% { transform: translate3d(120vw,30vh,0) rotate(40deg) scale(.8); opacity: 0; }
}

/* ============================================================
   Secret mystical heart (bottom-right easter egg trigger)
   ============================================================ */
#secret-rainbow-heart {
    position: absolute;
    bottom: 30px;
    right: 30px;
    width: 56px;
    height: 56px;
    cursor: pointer;
    user-select: none;
    pointer-events: auto;
    display: none;
    animation: secretFloat 2.6s ease-in-out infinite;
}

#secret-rainbow-heart.visible { display: block; }

#secret-rainbow-heart svg {
    width: 100%;
    height: 100%;
    filter: drop-shadow(0 4px 14px rgba(201, 167, 255, .55));
    animation: crystalShimmer 3.6s ease-in-out infinite;
}

@keyframes crystalShimmer {
    0%, 100% { filter: drop-shadow(0 4px 14px rgba(201,167,255,.55)) brightness(1); }
    50%      { filter: drop-shadow(0 4px 20px rgba(255,110,187,.65)) brightness(1.15); }
}

@keyframes secretFloat {
    0%, 100% { transform: translateY(0); }
    50%      { transform: translateY(-8px); }
}

/* ============================================================
   Falling heart — smooth, crisp trajectory + soft trailing glow
   ============================================================ */
.dropping-pink-heart {
    position: absolute;
    top: -100px;
    width: 78px;
    height: 78px;
    z-index: 5;
    will-change: transform;
    animation: fireworkFloatDown linear forwards;
}

.dropping-pink-heart svg {
    width: 100%;
    height: 100%;
    filter: drop-shadow(0 0 10px rgba(255,110,187,.65));
}

.heart-glow-trail {
    position: absolute;
    top: -100px;
    width: 78px;
    height: 78px;
    z-index: 4;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(255,110,187,.35) 0%, transparent 70%);
    filter: blur(6px);
    will-change: transform, opacity;
    animation: fireworkFloatDown linear forwards;
    animation-delay: -160ms;
    opacity: .55;
}

@keyframes fireworkFloatDown {
    0%   { transform: translate3d(0,0,0) scale(.5); opacity: 0; }
    14%  { opacity: 1; transform: translate3d(0, calc(var(--target-y) * .15), 0) scale(1.05); }
    100% { transform: translate3d(0, var(--target-y), 0) scale(1); opacity: 1; }
}

/* ============================================================
   Pop burst — minimal, crisp, no glyph clutter
   ============================================================ */
.burst-pop-ring {
    position: absolute;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: 2px solid rgba(255, 182, 220, .75);
    transform: translate(-50%, -50%);
    z-index: 3;
    pointer-events: none;
    animation: popRingGrow .55s cubic-bezier(.1,.8,.3,1) forwards;
}

@keyframes popRingGrow {
    0%   { width: 10px; height: 10px; opacity: .9; border-width: 3px; }
    100% { width: 150px; height: 150px; opacity: 0; border-width: 1px; }
}

.burst-spark {
    position: absolute;
    border-radius: 50%;
    transform: translate(-50%, -50%);
    z-index: 4;
    pointer-events: none;
    will-change: transform, opacity;
    animation: sparkOut .8s cubic-bezier(.1,.85,.25,1) forwards;
}

@keyframes sparkOut {
    0%   { transform: translate(-50%,-50%) scale(1); opacity: 1; }
    100% { transform: translate(calc(-50% + var(--tx)), calc(-50% + var(--ty))) scale(0); opacity: 0; }
}

.burst-heart {
    position: absolute;
    width: 14px;
    height: 14px;
    transform: translate(-50%, -50%);
    z-index: 4;
    pointer-events: none;
    will-change: transform, opacity;
    filter: drop-shadow(0 0 4px rgba(255,110,187,.6));
    animation: sparkOut 1s cubic-bezier(.1,.85,.25,1) forwards;
}

.burst-heart svg {
    width: 100%;
    height: 100%;
}

/* ============================================================
   Light mode
   ============================================================ */
body.light-mode {
    background: radial-gradient(circle at 30% 20%, var(--cream) 0%, #fff9fb 60%, var(--blush) 100%);
}
body.light-mode::before { opacity: .4; }
body.light-mode .teaser-text:not(.cute-mode) { color: var(--mauve); }
body.light-mode .switch-housing {
    background: linear-gradient(160deg, #fff3f6 0%, #ffe9f1 100%);
    border-color: var(--rose-soft);
}
body.light-mode .switch-toggle {
    top: 24px;
    background: var(--rose);
}
body.light-mode .switch-aura {
    background: radial-gradient(circle, rgba(255,110,187,.2) 0%, transparent 70%);
}

/* Respect reduced-motion preference */
@media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
        animation-duration: .001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: .001ms !important;
    }
}
""";
}
