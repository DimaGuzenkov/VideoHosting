import http from 'k6/http';

const inDocker = __ENV.K6_IN_DOCKER === 'true';

export const HOST = inDocker ? 'http://api-gateway' : 'http://localhost';
export const BASE_URL = `${HOST}:8080`;

const MINIO_INTERNAL = 'http://minio-cache:9000';
const MINIO_LOCAL_RE = /https?:\/\/(localhost|127\.0\.0\.1):9000/g;

export function fixUrl(url) {
  if (!inDocker) return url;
  return url.replace(MINIO_LOCAL_RE, MINIO_INTERNAL);
}

export const USER = 'admin';
export const PASS = '123456';

export function resolveUrl(baseUrl, relative) {
  if (/^https?:\/\//.test(relative)) return relative;
  const dir = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
  return dir + relative;
}

export function loginAndGetToken(httpClient = http) {
  const login = httpClient.post(
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

export function fetchReadyVideoIds(httpClient = http, token) {
  const list = httpClient.get(`${BASE_URL}/api/videos`, {
    headers: { Authorization: `Bearer ${token}` },
    responseType: 'text',
  });
  if (list.status !== 200) {
    throw new Error(`Cannot fetch videos: status=${list.status} body=${list.body}`);
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

export function pickVariantUrl(masterBody, masterUrl) {
  const lines = masterBody
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l);

  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes('RESOLUTION=1280x720')) {
      return resolveUrl(masterUrl, lines[i + 1]);
    }
  }
  for (let i = 1; i < lines.length; i++) {
    if (lines[i - 1].startsWith('#EXT-X-STREAM-INF')) {
      return resolveUrl(masterUrl, lines[i]);
    }
  }
  return masterUrl;
}