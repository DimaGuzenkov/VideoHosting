import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { Trend } from 'k6/metrics';
import { BASE_URL, loginAndGetToken, fixUrl } from './config.js';

const initDuration = new Trend('upload_init_ms');
const partUploadDuration = new Trend('upload_part_ms');
const completeDuration = new Trend('upload_complete_ms');
const totalDuration = new Trend('upload_total_ms');

export const options = {
  discardResponseBodies: true,
  scenarios: {
    uploaders: {
      executor: 'constant-vus',
      vus: 3,
      duration: '3m',
    },
  },
};

// ⬇️⬇️⬇️ ЗАМЕНИТЕ ИМЯ ФАЙЛА НА ВАШЕ ⬇️⬇️⬇️
const VIDEOS = [
    { bytes: open('/scripts/media/video1.mp4', 'b'), name: 'video1.mp4' },
  ];
// ⬆️⬆️⬆️ ЗАМЕНИТЕ ИМЯ ФАЙЛА НА ВАШЕ ⬆️⬆️⬆️

function getEtag(headers) {
  for (const k of ['Etag', 'ETag', 'etag']) {
    if (headers[k]) return String(headers[k]).replace(/"/g, '');
  }
  return null;
}

export function setup() {
  return { token: loginAndGetToken(http) };
}

export default function (data) {
  const video = VIDEOS[__VU % VIDEOS.length];
  const fileName = `LOADTEST_${__VU}_${Date.now()}_${video.name}`;
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
      title: `LOADTEST-${Date.now()}`,
      description: 'k6 load test',
      fileName: fileName,
      fileSize: fileSize,
      contentType: 'video/mp4',
    }),
    { headers: jsonHeaders, responseType: 'text' }
  );
  initDuration.add(Date.now() - initStart);

  if (initRes.status !== 200) {
    console.log(`FAIL init status=${initRes.status} body=${initRes.body}`);
    return;
  }

  const init = initRes.json();
  const uploadId = init.uploadId;
  const objectKey = init.objectKey;
  const partSize = init.partSize;
  const parts = init.parts;

  // ===== ШАГ 2: UPLOAD PARTS =====
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

    if (putRes.status !== 200) {
      console.log(`FAIL part ${part.partNumber} status=${putRes.status} body=${putRes.body}`);
      return;
    }

    const etag = getEtag(putRes.headers);
    if (!etag) {
      console.log(`FAIL no etag part ${part.partNumber} headers=${JSON.stringify(putRes.headers)}`);
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
      title: `LOADTEST-${Date.now()}`,
      description: 'k6 load test',
      fileName: fileName,
      parts: completedParts,
    }),
    { headers: jsonHeaders, responseType: 'text' }
  );
  completeDuration.add(Date.now() - completeStart);
  totalDuration.add(Date.now() - totalStart);

  if (completeRes.status === 200) {
    const r = completeRes.json();
    console.log(
      `OK VU=${__VU} size=${(fileSize / 1024 / 1024).toFixed(1)}MB ` +
      `videoId=${r.id} total=${Date.now() - totalStart}ms`
    );
  } else {
    console.log(`FAIL complete status=${completeRes.status} body=${completeRes.body}`);
  }
}