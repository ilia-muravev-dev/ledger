import { describe, expect, it } from 'vitest';
import type { Account } from './api';
import type { LiveUpdate } from './live';
import { initialState, ledgerReducer } from './ledgerState';

const account = (id: string, balance: number): Account => ({
  id, owner: id, currency: 'EUR', type: 'CUSTOMER', status: 'ACTIVE', balance, createdAt: '2026-11-08T00:00:00Z',
});

const update = (accountId: string, entryId: number, balanceAfter: number): LiveUpdate => ({
  type: 'entry-posted', accountId, entryId, transferId: `t${entryId}`, transferType: 'TRANSFER',
  counterpartyAccountId: 'other', amount: 1, currency: 'EUR', balanceAfter, postedAt: '2026-11-08T00:00:00Z',
});

describe('ledgerReducer', () => {
  it('applies the newest balance per account and ignores stale or repeated updates', () => {
    const loaded = ledgerReducer(initialState, { type: 'accounts-loaded', accounts: [account('a', 0), account('b', 0)] });

    const next = ledgerReducer(loaded, { type: 'live', at: 1, updates: [update('a', 7, 70), update('a', 5, 50)] });
    const repeated = ledgerReducer(next, { type: 'live', at: 2, updates: [update('a', 7, 999)] });

    expect(repeated.accounts.find((a) => a.id === 'a')?.balance).toBe(70);
    expect(repeated.accounts.find((a) => a.id === 'b')?.balance).toBe(0);
    expect(repeated.flashed).toEqual({ a: 1 });
  });

  it('prepends new lines to the open statement once, newest first', () => {
    let state = ledgerReducer(initialState, { type: 'accounts-loaded', accounts: [account('a', 0)] });
    state = ledgerReducer(state, { type: 'statement-loaded', accountId: 'a', lines: [] });

    state = ledgerReducer(state, { type: 'live', at: 1, updates: [update('a', 2, 20), update('a', 3, 30), update('a', 2, 20)] });

    expect(state.statement?.lines.map((line) => line.entryId)).toEqual([3, 2]);
  });

  it('does not let a live update roll back a balance loaded from a newer statement', () => {
    let state = ledgerReducer(initialState, { type: 'accounts-loaded', accounts: [account('a', 100)] });
    state = ledgerReducer(state, {
      type: 'statement-loaded', accountId: 'a',
      lines: [{ entryId: 10, transferId: 't', transferType: 'DEPOSIT', counterpartyAccountId: 's', amount: 100, currency: 'EUR', balanceAfter: 100, postedAt: '' }],
    });

    state = ledgerReducer(state, { type: 'live', at: 1, updates: [update('a', 9, 1)] });

    expect(state.accounts[0]?.balance).toBe(100);
  });
});
