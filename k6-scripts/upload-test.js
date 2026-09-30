import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const initDuration = new Trend('upload_init_ms');
const partUploadDuration = new Trend('upload_part_ms');
const completeDuration = new Trend('upload_complete_ms');
const totalDuration = new Trend('upload_total_ms');

export const options = {
  scenarios: {
    uploaders: {
      executor: 'constant-vus',
      vus: 3,
      duration: '3m',
    },
  },
  thresholds: {
    'upload_total_ms': ['p(95)<300000'],   // 95% загрузок быстрее 5 минут
  },
};

const BASE_URL = 'http://api-gateway:8080';
const USER = 'admin';
const PASS = '123456';

// ⬇️⬇️⬇️ ЗАМЕНИТЕ НА ВАШИ ФАЙЛЫ ⬇️⬇️⬇️
const VIDEOS = [
  { bytes: open('/scripts/media/video1.mp4', 'b'), name: 'video1.mp4' },
//  { bytes: open('/scripts/media/video2.mp4', 'b'), name: 'video2.mp4' },
//  { bytes: open('/scripts/media/video3.mp4', 'b'), name: 'video3.mp4' },
];
// ⬆️⬆️⬆️ ЗАМЕНИТЕ НА ВАШИ ФАЙЛЫ ⬆️⬆️⬆️

// Минио отдаёт presigned URL на localhost:9000 — изнутри k6 это не работает
// (localhost у k6 — это сам контейнер k6). Заменяем на имя контейнера MinIO
function fixUrl(url) {
  return url
    .replace('localhost:9000', 'minio:9000')
    .replace('localhost:9001', 'minio:9000');
}

// Достаём ETag из заголовков (k6 может отдать в разных регистрах)
function getEtag(headers) {
  const keys = ['Etag', 'ETag', 'etag'];
  for (const k of keys) {
    if (headers[k]) return String(headers[k]).replace(/"/g, '');
  }
  return null;
}

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' }, responseType: 'text' }
  );

  const token =
    login.json('token') ||
    login.json('accessToken') ||
    login.json('jwt');

  if (!token) throw new Error('No token: ' + login.body);
  console.log('[SETUP] Logged in successfully');
  return { token };
}

export default function (data) {
  const video = VIDEOS[__VU % VIDEOS.length];
  const fileName = `k6_${__VU}_${Date.now()}_${video.name}`;
  const fileSize = video.bytes.byteLength;

  const jsonHeaders = {
    'Authorization': `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };

  const totalStart = Date.now();

  // ===== ШАГ 1: INIT =====
  const initStart = Date.now();
  const initRes = http.post(
    `${BASE_URL}/api/videos/upload/init`,
    JSON.stringify({
      title: `k6-test-${Date.now()}`,
      description: 'k6 load test',
      fileName: fileName,
      fileSize: fileSize,
      contentType: 'video/mp4',
    }),
    { headers: jsonHeaders, responseType: 'text' }
  );
  initDuration.add(Date.now() - initStart);

  check(initRes, { 'init 200': (r) => r.status === 200 });
  if (initRes.status !== 200) {
    console.log(`FAIL init status=${initRes.status} body=${initRes.body}`);
    return;
  }

  const init = initRes.json();
  const uploadId = init.uploadId;
  const objectKey = init.objectKey;
  const partSize = init.partSize;
  const parts = init.parts;

  // ===== ШАГ 2: UPLOAD PARTS → напрямую в MinIO =====
  const completedParts = [];

  for (const part of parts) {
    const start = (part.partNumber - 1) * partSize;
    const end = Math.min(start + partSize, fileSize);
    const chunk = video.bytes.slice(start, end);

    const presignedUrl = fixUrl(part.presignedUrl);

    const partStart = Date.now();
    const putRes = http.put(presignedUrl, chunk, {
      headers: { 'Content-Type': 'application/octet-stream' },
      timeout: '10m',
    });
    partUploadDuration.add(Date.now() - partStart);

    check(putRes, { 'part upload 200': (r) => r.status === 200 });

    if (putRes.status !== 200) {
      console.log(
        `FAIL part ${part.partNumber} status=${putRes.status} ` +
        `body=${String(putRes.body).substring(0, 200)}`
      );
      return;
    }

    const etag = getEtag(putRes.headers);
    if (!etag) {
      console.log(`FAIL part ${part.partNumber} - no etag in headers`);
      return;
    }

    completedParts.push({
      partNumber: part.partNumber,
      etag: etag,
    });
  }

  // ===== ШАГ 3: COMPLETE =====
  const completeStart = Date.now();
  const completeRes = http.post(
    `${BASE_URL}/api/videos/upload/complete`,
    JSON.stringify({
      uploadId: uploadId,
      objectKey: objectKey,
      title: `k6-test-${Date.now()}`,
      description: 'k6 load test',
      fileName: fileName,
      parts: completedParts,
    }),
    { headers: jsonHeaders, responseType: 'text' }
  );
  completeDuration.add(Date.now() - completeStart);
  totalDuration.add(Date.now() - totalStart);

  check(completeRes, { 'complete 200': (r) => r.status === 200 });

  if (completeRes.status === 200) {
    const videoData = completeRes.json();
    console.log(
      `OK VU=${__VU} size=${(fileSize / 1024 / 1024).toFixed(1)}MB ` +
      `parts=${parts.length} videoId=${videoData.id} ` +
      `total=${Date.now() - totalStart}ms`
    );
  } else {
    console.log(`FAIL complete status=${completeRes.status} body=${completeRes.body}`);
  }
}