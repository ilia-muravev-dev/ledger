import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EUR_ACCOUNTS = Number(__ENV.EUR_ACCOUNTS || 200);
const USD_ACCOUNTS = Number(__ENV.USD_ACCOUNTS || 50);
const DURATION = __ENV.DURATION || '2m';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  setupTimeout: '5m',
  scenarios: {
    uniform: {
      executor: 'ramping-arrival-rate',
      exec: 'uniformTransfer',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 400,
      stages: [
        { target: 300, duration: '30s' },
        { target: 300, duration: DURATION },
        { target: 0, duration: '10s' },
      ],
    },
    hot_account: {
      executor: 'constant-arrival-rate',
      exec: 'hotAccountPayout',
      rate: 100,
      timeUnit: '1s',
      duration: DURATION,
      startTime: '30s',
      preAllocatedVUs: 50,
      maxVUs: 300,
    },
    fx: {
      executor: 'constant-arrival-rate',
      exec: 'fxTransfer',
      rate: 20,
      timeUnit: '1s',
      duration: DURATION,
      startTime: '30s',
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
    overdraft_attempts: {
      executor: 'constant-arrival-rate',
      exec: 'overdraftAttempt',
      rate: 5,
      timeUnit: '1s',
      duration: DURATION,
      startTime: '30s',
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
    client_retries: {
      executor: 'constant-arrival-rate',
      exec: 'retriedTransfer',
      rate: 10,
      timeUnit: '1s',
      duration: DURATION,
      startTime: '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{scenario:uniform}': ['p(95)<200', 'p(99)<500'],
    'http_req_duration{scenario:fx}': ['p(99)<500'],
    'http_req_duration{scenario:hot_account}': ['p(99)<500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function key(prefix) {
  return `${prefix}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`;
}

function post(path, body, idempotencyKey, tags) {
  const headers = idempotencyKey ? { ...JSON_HEADERS, 'Idempotency-Key': idempotencyKey } : JSON_HEADERS;
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), { headers, tags });
}

function openAccount(currency, funding) {
  const account = post('/accounts', { owner: 'k6', currency }).json();
  const deposit = post(`/accounts/${account.id}/deposits`, { amount: funding, currency }, `setup-${account.id}`);
  if (deposit.status !== 201) {
    throw new Error(`deposit failed: ${deposit.status} ${deposit.body}`);
  }
  return account.id;
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function setup() {
  const eur = [];
  for (let i = 0; i < EUR_ACCOUNTS; i++) {
    eur.push(openAccount('EUR', 10_000_000));
  }
  const usd = [];
  for (let i = 0; i < USD_ACCOUNTS; i++) {
    usd.push(openAccount('USD', 10_000_000));
  }
  const hot = openAccount('EUR', 1_000_000_000);
  const poor = openAccount('EUR', 1_000);
  return { eur, usd, hot, poor };
}

export function uniformTransfer(data) {
  const from = pick(data.eur);
  let to = pick(data.eur);
  while (to === from) {
    to = pick(data.eur);
  }
  const res = post('/transfers', { fromAccountId: from, toAccountId: to, amount: 1 + Math.floor(Math.random() * 500), currency: 'EUR' },
    key('uniform'), { name: 'POST /transfers' });
  check(res, { 'uniform: 201 or 409': (r) => r.status === 201 || r.status === 409 });
}

export function hotAccountPayout(data) {
  const res = post('/transfers', { fromAccountId: data.hot, toAccountId: pick(data.eur), amount: 1, currency: 'EUR' },
    key('hot'), { name: 'POST /transfers' });
  check(res, { 'hot: 201': (r) => r.status === 201 });
}

export function fxTransfer(data) {
  const res = post('/transfers', {
    fromAccountId: pick(data.eur), toAccountId: pick(data.usd), amount: 100 + Math.floor(Math.random() * 900),
    currency: 'EUR', targetCurrency: 'USD',
  }, key('fx'), { name: 'POST /transfers' });
  check(res, { 'fx: 201 with snapshot': (r) => r.status === 201 && r.json('fx.targetCurrency') === 'USD' });
}

export function overdraftAttempt(data) {
  const res = post('/transfers', { fromAccountId: data.poor, toAccountId: pick(data.eur), amount: 5_000, currency: 'EUR' },
    key('overdraft'), { name: 'POST /transfers' });
  check(res, { 'overdraft: 409 insufficient funds': (r) => r.status === 409 && r.json('reason') === 'INSUFFICIENT_FUNDS' });
}

export function retriedTransfer(data) {
  const from = pick(data.eur);
  let to = pick(data.eur);
  while (to === from) {
    to = pick(data.eur);
  }
  const body = { fromAccountId: from, toAccountId: to, amount: 7, currency: 'EUR' };
  const idempotencyKey = key('retry');
  const first = post('/transfers', body, idempotencyKey, { name: 'POST /transfers' });
  const second = post('/transfers', body, idempotencyKey, { name: 'POST /transfers (retry)' });
  check(second, {
    'retry: 200 with the same transfer': (r) => r.status === 200 && r.json('id') === first.json('id'),
  });
}

export function teardown() {
  const report = http.get(`${BASE_URL}/actuator/reconciliation`).json();
  check(report, { 'books are consistent after the run': (r) => r.consistent === true });
}
