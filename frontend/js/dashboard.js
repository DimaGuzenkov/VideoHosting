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

    if (!title || !file) {
        status.textContent = 'Заполните название и выберите файл.';
        return;
    }

    const token = getToken();
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    formData.append('description', description);

    try {
        const res = await fetch('/api/videos/upload', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token },
            body: formData
        });
        const { ok, data } = await parseJsonSafe(res);

        if (ok) {
            status.textContent = '✅ Загружено! ID: ' + data.id;
            document.getElementById('uploadTitle').value = '';
            document.getElementById('uploadDescription').value = '';
            fileInput.value = '';
            loadVideoList();   // сразу подгрузим список — увидим UPLOADED
        } else {
            status.textContent = '❌ Ошибка: ' + (data.error || 'Неизвестная ошибка');
        }
    } catch (e) {
        status.textContent = '❌ Ошибка сети: ' + e.message;
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