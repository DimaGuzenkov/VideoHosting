document.addEventListener('DOMContentLoaded', () => {
    if (!requireGuest()) return;

    document.getElementById('loginBtn').addEventListener('click', login);
    document.getElementById('registerBtn').addEventListener('click', register);

    // Enter в полях логина
    document.getElementById('loginPassword').addEventListener('keypress', e => {
        if (e.key === 'Enter') login();
    });
});

async function login() {
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value.trim();
    const msg = document.getElementById('loginMessage');

    if (!username || !password) {
        msg.textContent = 'Заполните все поля';
        return;
    }

    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const { ok, data } = await parseJsonSafe(res);
        if (ok) {
            setToken(data.token);
            redirectToDashboard();
        } else {
            msg.textContent = 'Ошибка: ' + (data.error || 'Неизвестная ошибка');
        }
    } catch (e) {
        msg.textContent = 'Ошибка сети: ' + e.message;
    }
}

async function register() {
    const username = document.getElementById('regUsername').value.trim();
    const email = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value.trim();
    const msg = document.getElementById('regMessage');

    if (!username || !email || !password) {
        msg.textContent = 'Заполните все поля';
        return;
    }

    try {
        const res = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, email, password })
        });
        const { ok, data } = await parseJsonSafe(res);
        if (ok) {
            setToken(data.token);
            redirectToDashboard();
        } else {
            msg.textContent = 'Ошибка: ' + (data.error || 'Неизвестная ошибка');
        }
    } catch (e) {
        msg.textContent = 'Ошибка сети: ' + e.message;
    }
}
