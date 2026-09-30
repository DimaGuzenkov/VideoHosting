import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    viewers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 200 },   // разгон до 200
        { duration: '2m', target: 200 },   // держим 200
        { duration: '1m', target: 400 },   // разгон до 400
        { duration: '2m', target: 400 },   // держим 400
        { duration: '1m', target: 600 },   // разгон до 600
        { duration: '2m', target: 600 },   // держим 600
        { duration: '1m', target: 800 },   // разгон до 800
        { duration: '2m', target: 800 },   // держим 800
        { duration: '1m', target: 1000 },  // разгон до 1000
        { duration: '2m', target: 1000 },  // держим 1000
        { duration: '1m', target: 0 },     // сброс
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
const VIDEO_ID = 34;

// Сколько сегментов «смотрит» один зритель за одну итерацию
// 33 сегмента × ~9 сек = ~5 минут просмотра
const SEGMENTS_PER_VIEW = 33;
const SEGMENT_DURATION_SEC = 9.2;

// Заменяем localhost:9000 на адрес nginx-кэша внутри Docker
function fixUrl(url) {
  return url.replace('localhost:9000', 'minio-cache:8080');
}

// Собираем абсолютный URL из относительного
function resolveUrl(baseUrl, relative) {
  if (relative.startsWith('http://') || relative.startsWith('https://')) {
    return relative;
  }
  const dir = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
  return dir + relative;
}

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    {
      headers: { 'Content-Type': 'application/json' },
      responseType: 'text',   // важно: иначе discardResponseBodies съест тело
    }
  );

  const token =
    login.json('token') ||
    login.json('accessToken') ||
    login.json('jwt');

  if (!token) {
    throw new Error('No token in login response: ' + login.body);
  }

  return { token };
}

export default function (data) {
  const headers = { headers: { Authorization: `Bearer ${data.token}` } };

  // 1. Получаем URL манифеста через API
  const playlistRes = http.get(
    `${BASE_URL}/api/stream/${VIDEO_ID}/playlist-url`,
    { ...headers, responseType: 'text' }
  );

  if (playlistRes.status !== 200) {
    sleep(1);
    return;
  }

  const masterUrl = fixUrl(playlistRes.json('playlistUrl'));

  // 2. Скачиваем master.m3u8
  const master = http.get(masterUrl, { responseType: 'text' });
  if (master.status !== 200) {
    sleep(1);
    return;
  }

  const lines = master.body
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l);

  // 3. Ищем вариант 720p. Если нет — берём первый попавшийся
  let variantUrl = null;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes('RESOLUTION=1960x1080')) {
      variantUrl = resolveUrl(masterUrl, lines[i + 1]);
      break;
    }
  }
  if (!variantUrl) {
    const variantLine = lines.find(
      (l, i) => lines[i - 1]?.startsWith('#EXT-X-STREAM-INF')
    );
    variantUrl = variantLine ? resolveUrl(masterUrl, variantLine) : masterUrl;
  }

  // Отладка: печатаем выбранный вариант (первая итерация каждого VU)
//  if (__ITER === 0) {
//    console.log(`VU ${__VU} variant: ${variantUrl}`);
//  }

  // 4. Скачиваем плейлист варианта
  const variant = http.get(variantUrl, { responseType: 'text' });
  if (variant.status !== 200) {
    sleep(1);
    return;
  }

  const baseDir = variantUrl.substring(0, variantUrl.lastIndexOf('/') + 1);
  const segments = variant.body
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'))
    .slice(0, SEGMENTS_PER_VIEW);

  // 5. Качаем сегменты. Тело НЕ читаем — экономим память
  for (const seg of segments) {
    const segUrl = resolveUrl(baseDir, seg);
    const r = http.get(segUrl, { responseType: 'none' });
    check(r, { 'segment 200': (res) => res.status === 200 });
    sleep(SEGMENT_DURATION_SEC - 1.5);
  }
}