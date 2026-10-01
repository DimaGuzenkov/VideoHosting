import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, loginAndGetToken, fetchReadyVideoIds } from './config.js';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 500 },
        { duration: '3m', target: 1000 },
        { duration: '2m', target: 1500 },
        { duration: '3m', target: 1500 },
        { duration: '2m', target: 2000 },
        { duration: '3m', target: 2000 },
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

export function setup() {
  const token = loginAndGetToken(http);
  const videoIds = fetchReadyVideoIds(http, token);
  return { token, videoIds };
}

export default function (data) {
  const headers = {
    headers: { Authorization: `Bearer ${data.token}` },
    responseType: 'text',
  };

  // 1. Список видео
  const list = http.get(`${BASE_URL}/api/videos`, headers);
  check(list, { 'list 200': (r) => r.status === 200 });

  // 2. URL плейлиста для случайного готового видео
  const videoId = data.videoIds[Math.floor(Math.random() * data.videoIds.length)];
  const playlist = http.get(`${BASE_URL}/api/stream/${videoId}/playlist-url`, headers);
  check(playlist, { 'playlist-url 200': (r) => r.status === 200 });

  sleep(1);
}