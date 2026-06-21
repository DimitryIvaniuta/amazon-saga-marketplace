import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const keyspace = Number(__ENV.MISSING_KEYSPACE || 100);

export const options = {
  scenarios: {
    repeated_missing_catalog_ids: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.MISSING_RATE || 500),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<200', 'p(99)<400'],
    dropped_iterations: ['count==0'],
  },
};

function missingUuid(index) {
  const suffix = String(index % keyspace).padStart(12, '0');
  return `ffffffff-ffff-4fff-8fff-${suffix}`;
}

export default function () {
  const id = missingUuid(__ITER);
  const response = http.get(`${baseUrl}/api/catalog/products/${id}`, {
    tags: { operation: 'catalog-negative-cache' },
  });
  check(response, {
    'missing product is rejected safely': (result) => result.status === 404 || result.status === 429,
  });
}
