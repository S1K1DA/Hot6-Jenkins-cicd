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
// ⚠️ 여기에 실제 토큰 넣는 거 잊지 마!
const TOKEN =
    'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdXBlckBhZG1pbi5jb20iLCJyb2xlIjoiU1VQRVJfQURNSU4iLCJ0eXBlIjoiQUNDRVNTIiwiaXNBZHVsdCI6ZmFsc2UsImlhdCI6MTc3NzczNjM0NywiZXhwIjoxNzc3NzU3OTQ3fQ.tiGkinrqaOxUogPZu24ltAnyuMLtjOyml8d6cy_lY8o';

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
    };

    // V1 API들을 batch로 한 번에 날림
    let responses = http.batch([
        ['GET', `${BASE_URL}/admin/dashboard/v1`, null, params],
        ['GET', `${BASE_URL}/admin/dashboard/v1?novelStatus=`, null, params],
        // 필요하면 통계 v1 API 더 추가해!
    ]);

    console.log("Response Body: " + responses[0].body);
    check(responses[0], { 'v1 status 200': (r) => r.status === 200 });

    sleep(1);
}