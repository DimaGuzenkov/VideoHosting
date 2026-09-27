import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    viewers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 800 },
        { duration: '2m', target: 800 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

const BASE_URL = 'http://api-gateway:8080';
const USER = 'admin';
const PASS = '123456';
const VIDEO_ID = 38;

// Сколько сегментов «смотрит» один зритель за одну итерацию
// 60 сегментов × 5 сек = 5 минут просмотра
const SEGMENTS_PER_VIEW = 33;
const SEGMENT_DURATION_SEC = 9.2;

function fixUrl(url) {
  return url.replace('localhost:9000', 'minio:9000');
}

function resolveUrl(baseUrl, relative) {
  if (relative.startsWith('http://') || relative.startsWith('https://')) return relative;
  const dir = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
  return dir + relative;
}

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const token = login.json('token') || login.json('accessToken') || login.json('jwt');
  if (!token) throw new Error('No token: ' + login.body);
  return { token };
}

export default function (data) {
  const headers = { headers: { Authorization: `Bearer ${data.token}` } };

  // 1. Получаем URL манифеста
  const playlistRes = http.get(`${BASE_URL}/api/stream/${VIDEO_ID}/playlist-url`, headers);
  if (playlistRes.status !== 200) { sleep(1); return; }
  const masterUrl = fixUrl(playlistRes.json('playlistUrl'));

  // 2. Манифест
  const master = http.get(masterUrl);
  if (master.status !== 200) { sleep(1); return; }

  // 3. Вариант качества (берём первый = обычно самый высокий)
  const lines = master.body.split('\n').map((l) => l.trim()).filter((l) => l);
  const variantLine = lines.find((l, i) => lines[i - 1]?.startsWith('#EXT-X-STREAM-INF'));
  const variantUrl = variantLine ? resolveUrl(masterUrl, variantLine) : masterUrl;
  const variant = variantUrl === masterUrl ? master : http.get(variantUrl);
  if (variant.status !== 200) { sleep(1); return; }

  // 4. Сегменты
  const baseDir = variantUrl.substring(0, variantUrl.lastIndexOf('/') + 1);
  const allSegments = variant.body
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'));

  const segmentsToWatch = allSegments.slice(0, SEGMENTS_PER_VIEW);

  // 5. Смотрим видео — качаем сегменты с реалистичными паузами
  for (const seg of segmentsToWatch) {
    const segUrl = resolveUrl(baseDir, seg);
    const r = http.get(segUrl);
    check(r, { 'segment 200': (res) => res.status === 200 });
    sleep(SEGMENT_DURATION_SEC - 1.5);
  }
}