export type Account = {
  id: string;
  owner: string;
  currency: string;
  type: string;
  status: 'ACTIVE' | 'CLOSED';
  balance: number;
  createdAt: string;
};

export type Fx = {
  targetAmount: number;
  targetCurrency: string;
  rate: number;
  rateSource: string;
  quotedAt: string;
};

export type Transfer = {
  id: string;
  type: 'TRANSFER' | 'DEPOSIT';
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
  status: 'COMPLETED' | 'REJECTED';
  reason?: string;
  fx?: Fx;
  createdAt: string;
};

export type StatementLine = {
  entryId: number;
  transferId: string;
  transferType: string;
  counterpartyAccountId: string;
  amount: number;
  currency: string;
  balanceAfter: number;
  postedAt: string;
};

export type Statement = {
  accountId: string;
  currency: string;
  balance: number;
  lastEntryId: number | null;
  items: StatementLine[];
  nextCursor?: string;
};

export type FxRate = { from: string; to: string; rate: number; source: string; quotedAt: string };

export type Problem = {
  type: string;
  title: string;
  status: number;
  detail?: string;
  reason?: string;
  transferId?: string;
  errors?: Record<string, string>;
};

export class ApiError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title);
    this.problem = problem;
  }
}

export type Outcome<T> = { status: number; body: T };

const BASE = '/api';

async function request<T>(path: string, init?: RequestInit): Promise<Outcome<T>> {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : undefined;
  if (!response.ok) {
    throw new ApiError(body ?? { type: 'about:blank', title: response.statusText, status: response.status });
  }
  return { status: response.status, body: body as T };
}

export const api = {
  async listAccounts(): Promise<Account[]> {
    const accounts: Account[] = [];
    let cursor: string | undefined;
    do {
      const query = cursor ? `?limit=200&cursor=${encodeURIComponent(cursor)}` : '?limit=200';
      const page = (await request<{ items: Account[]; nextCursor?: string }>(`/accounts${query}`)).body;
      accounts.push(...page.items);
      cursor = page.nextCursor;
    } while (cursor && accounts.length < 1000);
    return accounts;
  },

  async openAccount(owner: string, currency: string): Promise<Account> {
    return (await request<Account>('/accounts', { method: 'POST', body: JSON.stringify({ owner, currency }) })).body;
  },

  deposit(accountId: string, amount: number, currency: string, idempotencyKey: string) {
    return request<Transfer>(`/accounts/${accountId}/deposits`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify({ amount, currency }),
    });
  },

  transfer(
    body: { fromAccountId: string; toAccountId: string; amount: number; currency: string; targetCurrency?: string },
    idempotencyKey: string,
  ) {
    return request<Transfer>('/transfers', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(body),
    });
  },

  async statement(accountId: string): Promise<Statement> {
    return (await request<Statement>(`/accounts/${accountId}/statement?limit=50`)).body;
  },

  async fxRate(from: string, to: string): Promise<FxRate> {
    return (await request<FxRate>(`/fx/rates?from=${from}&to=${to}`)).body;
  },
};

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
