import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
        stages: [
//          { duration: '1m', target: 200 },
//          { duration: '2m', target: 200 },
//          { duration: '1m', target: 400 },
//          { duration: '2m', target: 400 },
//          { duration: '1m', target: 600 },
//          { duration: '2m', target: 600 },
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
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = 'http://api-gateway:8080';
const USER = 'admin';
const PASS = '123456';

const VIDEO_IDS = [34, 35, 36, 37, 38, 39, 40, 41, 42, 49];

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
  const headers = {
    headers: { Authorization: `Bearer ${data.token}` },
    responseType: 'text',
  };

  // 1. Список видео
  const list = http.get(`${BASE_URL}/api/videos`, headers);
  check(list, { 'list 200': (r) => r.status === 200 });

  // 2. URL плейлиста для случайного видео
  const videoId = VIDEO_IDS[Math.floor(Math.random() * VIDEO_IDS.length)];
  const playlist = http.get(`${BASE_URL}/api/stream/${videoId}/playlist-url`, headers);
  check(playlist, { 'playlist-url 200': (r) => r.status === 200 });

  sleep(1);
}