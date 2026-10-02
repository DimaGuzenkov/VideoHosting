import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, resolveUrl, loginAndGetToken, fetchReadyVideoIds, fixUrl, pickVariantUrl } from './config.js';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    viewers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 1000 },
        { duration: '2m', target: 2000 },
        { duration: '2m', target: 3000 },
        { duration: '3m', target: 3500 },
        { duration: '2m', target: 4000 },
        { duration: '3m', target: 4500 },
        { duration: '3m', target: 5000 },
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

  const playlistRes = http.get(
    `${BASE_URL}/api/stream/${videoId}/playlist-url`,
    { ...headers, responseType: 'text', tags: { name: 'playlist-url' } }
  );
  if (playlistRes.status !== 200) { sleep(1); return; }
  const masterUrl = fixUrl(playlistRes.json('playlistUrl'));

  const master = http.get(masterUrl, { responseType: 'text', tags: { name: 'master' } });
  if (master.status !== 200) { sleep(1); return; }

  const variantUrl = pickVariantUrl(master.body, masterUrl);

  const variant = http.get(variantUrl, { responseType: 'text', tags: { name: 'variant' } });
  if (variant.status !== 200) { sleep(1); return; }

  const baseDir = variantUrl.substring(0, variantUrl.lastIndexOf('/') + 1);
  const segments = variant.body
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'))
    .slice(0, SEGMENTS_PER_VIEW);

  for (const seg of segments) {
    const r = http.get(resolveUrl(baseDir, seg), {
      responseType: 'none',
      tags: { name: 'segment' },
    });
    check(r, { 'segment 200': (res) => res.status === 200 });
    sleep(SEGMENT_DURATION_SEC);
  }
}