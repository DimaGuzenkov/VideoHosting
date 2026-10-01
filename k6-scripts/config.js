// Автоопределение окружения через переменную K6_IN_DOCKER
const inDocker = __ENV.K6_IN_DOCKER === 'true';

// В Docker: k6 → api-gateway (внутренняя DNS-имя)
// На хосте: k6.exe → localhost
export const HOST = inDocker ? 'http://api-gateway' : 'http://localhost';
export const BASE_URL = `${HOST}:8080`;

// Presigned URL от MinIO содержат localhost:9000 (из MINIO_PUBLIC_ENDPOINT).
// В Docker это не резолвится — заменяем на имя контейнера nginx-cache.
const MINIO_INTERNAL = 'http://minio-cache:9000';

export function fixUrl(url) {
  if (!inDocker) return url;
  return url
    .replace('http://localhost:9000', MINIO_INTERNAL)
    .replace('http://127.0.0.1:9000', MINIO_INTERNAL);
}

export const USER = 'admin';
export const PASS = '123456';

export function resolveUrl(baseUrl, relative) {
  if (relative.startsWith('http://') || relative.startsWith('https://')) return relative;
  const dir = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
  return dir + relative;
}

export function loginAndGetToken(http) {
  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' }, responseType: 'text' }
  );
  const token =
    login.json('token') ||
    login.json('accessToken') ||
    login.json('jwt');
  if (!token) throw new Error('Login failed: ' + login.body);
  return token;
}

export function fetchReadyVideoIds(http, token) {
  const list = http.get(`${BASE_URL}/api/videos`, {
    headers: { Authorization: `Bearer ${token}` },
    responseType: 'text',
  });
  if (list.status !== 200) {
    throw new Error('Cannot fetch videos: status=' + list.status + ' body=' + list.body);
  }

  const parsed = list.json();
  const items = Array.isArray(parsed) ? parsed : (parsed.content || []);

  const ids = items
    .filter((v) => v.status === 'READY' && v.playlistPath)
    .map((v) => v.id);

  if (ids.length === 0) {
    throw new Error('No READY videos with playlist found');
  }

  console.log(`Loaded ${ids.length} READY videos: [${ids.join(', ')}]`);
  return ids;
}