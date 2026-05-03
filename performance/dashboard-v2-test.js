import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 20 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'],
    },
};

const BASE_URL = 'http://localhost:8080/api';
// ⚠️ V1과 똑같은 토큰을 넣어줘!
const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdXBlckBhZG1pbi5jb20iLCJyb2xlIjoiU1VQRVJfQURNSU4iLCJ0eXBlIjoiQUNDRVNTIiwiaXNBZHVsdCI6ZmFsc2UsImlhdCI6MTc3NzczNjM0NywiZXhwIjoxNzc3NzU3OTQ3fQ.tiGkinrqaOxUogPZu24ltAnyuMLtjOyml8d6cy_lY8o';

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
    };

    // V2 병합 API 호출
    let res1 = http.get(`${BASE_URL}/admin/dashboard/v2`, params);
    let res2 = http.get(`${BASE_URL}/admin/dashboard/v2?novelStatus=`, params);

    check(res1, { 'v2 status 200': (r) => r.status === 200 });

    sleep(1);
}