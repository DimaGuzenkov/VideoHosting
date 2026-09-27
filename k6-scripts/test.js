import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '2m', target: 100 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = 'http://api-gateway:8080';
const USER = 'admin';
const PASS = '123456';

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const token = login.json('token') || login.json('accessToken') || login.json('jwt');
  if (!token) throw new Error('No token: ' + login.body);

  const list = http.get(`${BASE_URL}/api/videos`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  console.log('LIST_BODY_FIRST_500: ' + list.body.substring(0, 500));
  console.log('LIST_STATUS: ' + list.status);

  const parsed = list.json();
  const items = Array.isArray(parsed) ? parsed : (parsed.content || []);
  console.log('ITEMS_COUNT: ' + items.length);
  if (items.length > 0) {
    console.log('FIRST_ITEM_KEYS: ' + Object.keys(items[0]).join(','));
    console.log('FIRST_ITEM_STATUS: ' + items[0].status);
    console.log('FIRST_ITEM_ID: ' + (items[0].id || items[0].videoId || items[0].uuid));
  }

  const ready = items.find((v) => v.status === 'READY') || items[0];
  const videoId = ready?.id || ready?.videoId || ready?.uuid;

  return { token, videoId };
}

export default function (data) {
  const headers = { headers: { Authorization: `Bearer ${data.token}` } };

  // 1. Список видео
  const list = http.get(`${BASE_URL}/api/videos`, headers);
  check(list, { 'list 200': (r) => r.status === 200 });

  // 2. Детали конкретного видео
  if (data.videoId) {
    const detail = http.get(`${BASE_URL}/api/stream/${data.videoId}/playlist-url`, headers);
    check(detail, { 'detail 200': (r) => r.status === 200 });
  }

  sleep(1);
}
