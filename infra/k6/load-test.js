import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const blockedCount = new Counter('blocked_decisions');
const allowedCount = new Counter('allowed_decisions');
const errorRate = new Rate('error_rate');
const decisionLatency = new Trend('decision_latency_ms');

export const options = {
    stages: [
        { duration: '30s', target: 50 },   // ramp up to 50 users
        { duration: '60s', target: 100 },  // ramp up to 100 users
        { duration: '60s', target: 200 },  // ramp up to 200 users
        { duration: '30s', target: 0 },    // ramp down
    ],
    thresholds: {
        http_req_duration: ['p(99)<500'],  // 99% of requests under 500ms
        error_rate: ['rate<0.01'],         // less than 1% errors
    },
};

export default function () {
    const txId = `tx_k6_${__VU}_${__ITER}`;
    const userId = `user_${Math.floor(Math.random() * 100)}`;
    const amount = Math.floor(Math.random() * 10000);

    const payload = JSON.stringify({
        transaction_id: txId,
        user_id: userId,
        amount: amount,
        device_fingerprint: `device_${__VU}`,
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
        timeout: '5s',
    };

    const start = Date.now();
    const res = http.post('http://localhost:8080/v1/check', payload, params);
    const latency = Date.now() - start;

    decisionLatency.add(latency);

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
        'has decision': (r) => r.json('decision') !== undefined,
    });

    errorRate.add(!success);

    if (res.status === 200) {
        const decision = res.json('decision');
        if (decision === 'BLOCK') {
            blockedCount.add(1);
        } else {
            allowedCount.add(1);
        }
    }

    sleep(0.1);
}
