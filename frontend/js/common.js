// === Токен ===
function getToken() {
    return localStorage.getItem('jwt');
}

function setToken(token) {
    localStorage.setItem('jwt', token);
}

function removeToken() {
    localStorage.removeItem('jwt');
}

// === Безопасный парсинг JSON ===
async function parseJsonSafe(response) {
    const text = await response.text();
    try {
        return { ok: response.ok, status: response.status, data: JSON.parse(text) };
    } catch {
        return { ok: response.ok, status: response.status, data: { error: text.substring(0, 300) } };
    }
}

// === Редиректы ===
function redirectToDashboard() {
    window.location.href = '/dashboard.html';
}

function redirectToLogin() {
    window.location.href = '/login.html';
}

// === Проверка авторизации (для защищённых страниц) ===
function requireAuth() {
    if (!getToken()) {
        redirectToLogin();
        return false;
    }
    return true;
}

// === Проверка отсутствия авторизации (для login.html) ===
function requireGuest() {
    if (getToken()) {
        redirectToDashboard();
        return false;
    }
    return true;
}
