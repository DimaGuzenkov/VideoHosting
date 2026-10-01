import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, resolveUrl, loginAndGetToken, fetchReadyVideoIds, fixUrl } from './config.js';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    viewers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 500 },
        { duration: '3m', target: 500 },
        { duration: '2m', target: 1000 },
        { duration: '3m', target: 1000 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

const SEGMENTS_PER_VIEW = 20;
const SEGMENT_DURATION_SEC = 9.2;

export function setup() {
  const token = loginAndGetToken(http);
  const videoIds = fetchReadyVideoIds(http, token);
  return { token, videoIds };
}

export default function (data) {
  const headers = { headers: { Authorization: `Bearer ${data.token}` } };
  const videoId = data.videoIds[Math.floor(Math.random() * data.videoIds.length)];

  // 1. URL манифеста
  const playlistRes = http.get(
    `${BASE_URL}/api/stream/${videoId}/playlist-url`,
    { ...headers, responseType: 'text' }
  );
  if (playlistRes.status !== 200) { sleep(1); return; }
  const masterUrl = fixUrl(playlistRes.json('playlistUrl'));

  // 2. Master playlist
  const master = http.get(masterUrl, { responseType: 'text' });
  if (master.status !== 200) { sleep(1); return; }

  // 3. Вариант 720p (с fallback на первый)
  const lines = master.body.split('\n').map((l) => l.trim()).filter((l) => l);
  let variantUrl = null;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes('RESOLUTION=1280x720')) {
      variantUrl = resolveUrl(masterUrl, lines[i + 1]);
      break;
    }
  }
  if (!variantUrl) {
    const variantLine = lines.find((l, i) => lines[i - 1]?.startsWith('#EXT-X-STREAM-INF'));
    variantUrl = variantLine ? resolveUrl(masterUrl, variantLine) : masterUrl;
  }

  const variant = http.get(variantUrl, { responseType: 'text' });
  if (variant.status !== 200) { sleep(1); return; }

  // 4. Сегменты
  const baseDir = variantUrl.substring(0, variantUrl.lastIndexOf('/') + 1);
  const segments = variant.body
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'))
    .slice(0, SEGMENTS_PER_VIEW);

  for (const seg of segments) {
    const r = http.get(resolveUrl(baseDir, seg), { responseType: 'none' });
    check(r, { 'segment 200': (res) => res.status === 200 });
    sleep(SEGMENT_DURATION_SEC - 1.5);
  }
}