let hls = null;

document.addEventListener('DOMContentLoaded', () => {
    if (!requireAuth()) return;

    const params = new URLSearchParams(window.location.search);
    const videoId = params.get('id');
    const statusEl = document.getElementById('status');

    if (!videoId) {
        statusEl.textContent = '❌ Не указан ID видео';
        statusEl.style.color = '#ff6b6b';
        return;
    }

    loadVideo(videoId);
});

async function loadVideo(videoId) {
    const token = getToken();
    const statusEl = document.getElementById('status');
    statusEl.textContent = '⏳ Загрузка...';

    try {
        const res = await fetch('/api/stream/' + videoId + '/playlist-url', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        const { ok, data } = await parseJsonSafe(res);

        if (!ok) {
            statusEl.textContent = '❌ ' + (data.error || 'Ошибка загрузки');
            statusEl.style.color = '#ff6b6b';
            return;
        }

        await loadVideoTitle(videoId, token);
        initPlayer(data.playlistUrl);
    } catch (e) {
        statusEl.textContent = '❌ Ошибка сети: ' + e.message;
        statusEl.style.color = '#ff6b6b';
    }
}

function initPlayer(playlistUrl) {
    const video = document.getElementById('video');
    const qualityBar = document.getElementById('qualityBar');
    const selector = document.getElementById('qualitySelector');
    const statusEl = document.getElementById('status');

    selector.innerHTML = '';

    if (Hls.isSupported()) {
        if (hls) hls.destroy();
        hls = new Hls({ capLevelToPlayerSize: false });
        hls.loadSource(playlistUrl);
        hls.on(Hls.Events.LEVEL_SWITCHED, () => {
            video.removeAttribute('width');
            video.removeAttribute('height');
        });
        hls.attachMedia(video);

        hls.on(Hls.Events.MANIFEST_PARSED, () => {
            const levels = hls.levels;
            const seen = new Set();
            const sorted = levels
                .map((lvl, idx) => ({ height: lvl.height, index: idx }))
                .sort((a, b) => b.height - a.height);

            const autoOpt = document.createElement('option');
            autoOpt.value = '-1';
            autoOpt.textContent = 'Авто';
            selector.appendChild(autoOpt);

            sorted.forEach(lvl => {
                if (lvl.height && !seen.has(lvl.height)) {
                    seen.add(lvl.height);
                    const opt = document.createElement('option');
                    opt.value = lvl.index;
                    opt.textContent = lvl.height + 'p';
                    selector.appendChild(opt);
                }
            });

            selector.value = '-1';
            qualityBar.style.display = 'flex';
            video.play().catch(() => {});
            statusEl.textContent = '';
        });

        hls.on(Hls.Events.ERROR, (event, data) => {
            if (data.fatal) {
                statusEl.textContent = '❌ Ошибка воспроизведения: ' + data.details;
                statusEl.style.color = '#ff6b6b';
            }
        });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = playlistUrl;
        video.addEventListener('loadedmetadata', () => video.play());
        statusEl.textContent = '';
    } else {
        statusEl.textContent = '❌ HLS не поддерживается браузером';
    }

    selector.onchange = function () {
        if (hls) hls.currentLevel = parseInt(this.value);
    };
}

async function loadVideoTitle(videoId, token) {
    try {
        const res = await fetch('/api/videos/' + videoId, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            const video = await res.json();
            document.getElementById('title').textContent = video.title || '';
        }
    } catch { /* ignore */ }
}
