import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Кастомные метрики
const uploadDuration = new Trend('upload_duration_ms');
const uploadAccepted = new Counter('upload_accepted_total');
const uploadFailed = new Counter('upload_failed_total');

export const options = {
  scenarios: {
    uploaders: {
      executor: 'constant-vus',
      vus: 3,              // 3 параллельные загрузки
      duration: '3m',
    },
  },
  thresholds: {
    'upload_duration_ms': ['p(95)<120000'],   // 95% загрузок быстрее 2 минут
    'upload_failed_total': ['count<5'],
  },
};

const BASE_URL = 'http://api-gateway:8080';
const USER = 'admin';
const PASS = '123456';

// ⬇️⬇️⬇️ ЗАМЕНИТЕ ПОД СВОИ ФАЙЛЫ ⬇️⬇️⬇️
// Файлы должны лежать в k6-scripts/media/
const VIDEOS = [
  { file: open('/scripts/media/video1.mp4', 'b'), name: 'video1.mp4', size: 'small' },
  { file: open('/scripts/media/video2.mp4', 'b'), name: 'video2.mp4', size: 'medium' },
//  { file: open('/scripts/media/video3.mp4', 'b'), name: 'video3.mp4', size: 'large' },
];
// ⬆️⬆️⬆️ ЗАМЕНИТЕ ПОД СВОИ ФАЙЛЫ ⬆️⬆️⬆️

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    {
      headers: { 'Content-Type': 'application/json' },
      responseType: 'text',
    }
  );

  const token =
    login.json('token') ||
    login.json('accessToken') ||
    login.json('jwt');

  if (!token) {
    throw new Error('Login failed: ' + login.body);
  }

  console.log(`[SETUP] Logged in successfully`);
  return { token };
}

export default function (data) {
  const video = VIDEOS[__VU % VIDEOS.length];

  const formData = {
    file: http.file(video.file, video.name, 'video/mp4'),
  };

  const startTime = Date.now();

  const res = http.post(`${BASE_URL}/api/upload`, formData, {
    headers: {
      'Authorization': `Bearer ${data.token}`,
    },
    timeout: '10m',
  });

  const durationMs = Date.now() - startTime;
  uploadDuration.add(durationMs);

  const accepted = res.status === 200 || res.status === 202;

  if (accepted) {
    uploadAccepted.add(1);
    console.log(
      `[UPLOAD OK] VU=${__VU} file=${video.name} ` +
      `size=${video.size} status=${res.status} duration=${durationMs}ms`
    );
  } else {
    uploadFailed.add(1);
    console.log(
      `[UPLOAD FAIL] VU=${__VU} file=${video.name} ` +
      `status=${res.status} duration=${durationMs}ms body=${res.body ? res.body.substring(0, 200) : ''}`
    );
  }

  check(res, {
    'upload accepted (200/202)': (r) => r.status === 200 || r.status === 202,
  });

  // Пауза между итерациями, чтобы не спамить непрерывно
  // Для стресс-теста можно убрать
  // sleep(2);
}