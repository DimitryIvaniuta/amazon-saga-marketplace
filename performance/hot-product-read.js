import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const productId = __ENV.PRODUCT_ID || '10000000-0000-0000-0000-000000000001';
const skuId = __ENV.SKU_ID || '20000000-0000-0000-0000-000000000001';

export const options = {
  scenarios: {
    hot_product_reads: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { target: 100, duration: '30s' },
        { target: 500, duration: '60s' },
        { target: 1000, duration: '30s' },
        { target: 100, duration: '30s' }
      ]
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{resource:product}': ['p(95)<150', 'p(99)<350'],
    'http_req_duration{resource:sku}': ['p(95)<150', 'p(99)<350']
  }
};

export default function () {
  const product = http.get(`${baseUrl}/api/catalog/products/${productId}`, {
    tags: { resource: 'product' }
  });
  check(product, { 'product returned 200': response => response.status === 200 });

  const sku = http.get(`${baseUrl}/api/catalog/skus/${skuId}`, {
    tags: { resource: 'sku' }
  });
  check(sku, { 'sku returned 200': response => response.status === 200 });
  sleep(0.01);
}
