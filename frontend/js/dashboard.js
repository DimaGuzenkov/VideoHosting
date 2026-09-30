let eventSource = null;

document.addEventListener('DOMContentLoaded', () => {
    if (!requireAuth()) return;

    const uploadBtn = document.getElementById('uploadBtn');
    if (uploadBtn) uploadBtn.addEventListener('click', uploadVideo);

    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) logoutBtn.addEventListener('click', logout);

    loadVideoList();
    connectSse();
});

// ============================================================
// SSE — подписка на уведомления об изменении статуса видео
// ============================================================

function connectSse() {
    const token = getToken();
    if (!token) return;

    // Закрываем старое соединение, если есть
    if (eventSource) {
        eventSource.close();
        eventSource = null;
    }

    // EventSource не поддерживает заголовки — токен в query
    const url = '/api/events?token=' + encodeURIComponent(token);
    eventSource = new EventSource(url);

    eventSource.addEventListener('connected', () => {
        console.log('✅ SSE connected');
    });

    eventSource.addEventListener('video-status', (e) => {
        try {
            const data = JSON.parse(e.data);
            console.log('📺 Video status update:', data);
            updateVideoInList(data.videoId, data.status);
        } catch (err) {
            console.warn('Failed to parse SSE event:', err);
        }
    });

    eventSource.onerror = (err) => {
        console.warn('SSE connection error, reconnecting in 5s...', err);
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
        setTimeout(connectSse, 5000);
    };
}

function updateVideoInList(videoId, status) {
    const el = document.querySelector(`[data-video-id="${videoId}"]`);
    if (!el) {
        // Видео ещё не в списке (например, добавили с другого устройства) — обновим список
        loadVideoList();
        return;
    }

    // Обновляем badge статуса
    const badge = el.querySelector('.video-status');
    if (badge) {
        badge.textContent = status;
        badge.className = 'badge video-status ms-2 ' + statusBadgeClass(status);
    }

    // Если статус READY — добавляем кнопку "Смотреть", если её ещё нет
    if (status === 'READY') {
        const actions = el.querySelector('.video-actions');
        if (actions && !actions.querySelector('a.btn-success')) {
            const link = document.createElement('a');
            link.href = '/player.html?id=' + videoId;
            link.className = 'btn btn-sm btn-success';
            link.textContent = 'Смотреть';
            actions.prepend(link);
        }
    }
}

function statusBadgeClass(status) {
    switch (status) {
        case 'READY':      return 'bg-success';
        case 'PROCESSING': return 'bg-warning text-dark';
        case 'UPLOADED':   return 'bg-secondary';
        case 'FAILED':     return 'bg-danger';
        default:           return 'bg-secondary';
    }
}

// ============================================================
// Список видео
// ============================================================

async function loadVideoList() {
    const container = document.getElementById('videoList');
    const status = document.getElementById('videoListStatus');
    const token = getToken();

    try {
        const res = await fetch('/api/videos', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        const { ok, data } = await parseJsonSafe(res);

        if (!ok) {
            if (res.status === 401 || res.status === 403) {
                logout();
                return;
            }
            status.textContent = 'Ошибка загрузки списка';
            return;
        }

        if (data.length === 0) {
            container.innerHTML = '<p class="text-muted">Нет загруженных видео.</p>';
        } else {
            container.innerHTML = data.map(v => `
                <div class="video-item d-flex justify-content-between align-items-center"
                     data-video-id="${v.id}">
                    <div>
                        <strong>${escapeHtml(v.title)}</strong>
                        <span class="badge video-status ms-2 ${statusBadgeClass(v.status)}">
                            ${v.status}
                        </span>
                    </div>
                    <div class="video-actions">
                        ${v.status === 'READY'
                            ? `<a href="/player.html?id=${v.id}" class="btn btn-sm btn-success">Смотреть</a>`
                            : ''}
                        <button onclick="deleteVideo(${v.id})" class="btn btn-sm btn-danger ms-1">
                            Удалить
                        </button>
                    </div>
                </div>
            `).join('');
        }
        status.textContent = '';
    } catch (e) {
        status.textContent = 'Ошибка: ' + e.message;
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ============================================================
// Загрузка видео
// ============================================================

async function uploadVideo() {
    const title = document.getElementById('uploadTitle').value.trim();
    const description = document.getElementById('uploadDescription').value.trim();
    const fileInput = document.getElementById('uploadFile');
    const file = fileInput.files[0];
    const status = document.getElementById('uploadStatus');
    const progress = document.getElementById('uploadProgress');

    if (!title || !file) {
        status.textContent = 'Заполните название и выберите файл.';
        return;
    }

    const token = getToken();
    status.textContent = '⏳ Инициализация...';
    progress.style.display = 'block';
    progress.value = 0;

    try {
        // 1. Init
        const initRes = await fetch('/api/videos/upload/init', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                title,
                description,
                fileName: file.name,
                fileSize: file.size,
                contentType: file.type || 'video/mp4'
            })
        });
        if (!initRes.ok) throw new Error('Init failed: ' + initRes.status);
        const init = await initRes.json();

        // 2. Upload parts (параллельно, max 3 в полёте)
        const uploaded = [];
        const CONCURRENCY = 3;
        let completed = 0;

        for (let i = 0; i < init.parts.length; i += CONCURRENCY) {
            const batch = init.parts.slice(i, i + CONCURRENCY);
            const results = await Promise.all(batch.map(async (part) => {
                const start = (part.partNumber - 1) * init.partSize;
                const end = Math.min(start + init.partSize, file.size);
                const chunk = file.slice(start, end);

                const res = await fetch(part.presignedUrl, {
                    method: 'PUT',
                    body: chunk
                });
                if (!res.ok) throw new Error(`Part ${part.partNumber} failed: ${res.status}`);

                const etag = res.headers.get('ETag');
                if (!etag) throw new Error(`No ETag for part ${part.partNumber}`);

                completed++;
                progress.value = Math.round(completed / init.parts.length * 100);
                status.textContent = `⬆️ Загрузка ${completed}/${init.parts.length} (${progress.value}%)`;

                return { partNumber: part.partNumber, etag };
            }));
            uploaded.push(...results);
        }

        // 3. Complete
        status.textContent = '✅ Сборка файла...';
        const completeRes = await fetch('/api/videos/upload/complete', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                uploadId: init.uploadId,
                objectKey: init.objectKey,
                title,
                description,
                fileName: file.name,
                parts: uploaded
            })
        });
        const result = await completeRes.json();
        if (!completeRes.ok) throw new Error(result.error || 'Complete failed');

        status.textContent = '🎉 Видео загружено! ID: ' + result.id;
        progress.style.display = 'none';
        document.getElementById('uploadTitle').value = '';
        document.getElementById('uploadDescription').value = '';
        fileInput.value = '';
        loadVideoList();

    } catch (e) {
        console.error('Upload error:', e);
        status.textContent = '❌ ' + e.message;
        progress.style.display = 'none';
    }
}

// ============================================================
// Удаление
// ============================================================

async function deleteVideo(videoId) {
    if (!confirm('Удалить видео?')) return;
    const token = getToken();
    try {
        const res = await fetch('/api/videos/' + videoId, {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            loadVideoList();
        } else {
            const { data } = await parseJsonSafe(res);
            alert('Ошибка удаления: ' + (data.error || res.status));
        }
    } catch (e) {
        alert('Ошибка: ' + e.message);
    }
}

// ============================================================
// Logout
// ============================================================

function logout() {
    if (eventSource) {
        eventSource.close();
        eventSource = null;
    }
    removeToken();
    redirectToLogin();
}

// Закрываем SSE при закрытии вкладки
window.addEventListener('beforeunload', () => {
    if (eventSource) eventSource.close();
});