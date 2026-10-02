import http from 'k6/http';
import { check, sleep } from 'k6';
import { resolveUrl, pickVariantUrl } from './config.js';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    viewers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 200 },
        { duration: '2m', target: 200 },
        { duration: '1m', target: 400 },
        { duration: '2m', target: 400 },
        { duration: '1m', target: 600 },
        { duration: '2m', target: 600 },
        { duration: '1m', target: 800 },
        { duration: '2m', target: 800 },
        { duration: '1m', target: 1000 },
        { duration: '2m', target: 1000 },
        { duration: '1m', target: 0 },
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
const VIDEO_ID = 456;
const SEGMENTS_PER_VIEW = 33;
const SEGMENT_DURATION_SEC = 1.5;

function fixUrl(url) {
  return url.replace(/https?:\/\/(localhost|127\.0\.0\.1):9000/g, 'http://minio-cache:9000');
}

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' }, responseType: 'text' }
  );
  const token = login.json('token') || login.json('accessToken') || login.json('jwt');
  if (!token) throw new Error('No token: ' + login.body);
  return { token };
}

export default function (data) {
  const headers = { headers: { Authorization: `Bearer ${data.token}` } };

  const playlistRes = http.get(
    `${BASE_URL}/api/stream/${VIDEO_ID}/playlist-url`,
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