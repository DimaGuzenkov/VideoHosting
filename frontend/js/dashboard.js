document.addEventListener('DOMContentLoaded', () => {
    if (!requireAuth()) return;

    loadVideoList();
});

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
                <div class="video-item d-flex justify-content-between align-items-center">
                    <div>
                        <strong>${v.title}</strong>
                        <span class="badge bg-secondary ms-2">${v.status}</span>
                        ${v.status === 'READY' ? '<span class="badge bg-success ms-1">готово</span>' : ''}
                    </div>
                    <div>
                        ${v.status === 'READY'
                            ? `<a href="/player.html?id=${v.id}" class="btn btn-sm btn-success">Смотреть</a>`
                            : ''}
                        <button onclick="deleteVideo(${v.id})" class="btn btn-sm btn-danger ms-1">Удалить</button>
                    </div>
                </div>
            `).join('');
        }
        status.textContent = '';
    } catch (e) {
        status.textContent = 'Ошибка: ' + e.message;
    }
}

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
            loadVideoList();
        } else {
            status.textContent = '❌ Ошибка: ' + (data.error || 'Неизвестная ошибка');
        }
    } catch (e) {
        status.textContent = '❌ Ошибка сети: ' + e.message;
    }
}

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

function logout() {
    removeToken();
    redirectToLogin();
}
