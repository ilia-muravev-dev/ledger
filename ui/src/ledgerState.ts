import type { Account, StatementLine } from './api';
import type { LiveUpdate } from './live';

export type LedgerState = {
  accounts: Account[];
  lastEntryIds: Record<string, number>;
  statement: { accountId: string; lines: StatementLine[] } | null;
  flashed: Record<string, number>;
};

export type LedgerAction =
  | { type: 'accounts-loaded'; accounts: Account[] }
  | { type: 'account-opened'; account: Account }
  | { type: 'statement-loaded'; accountId: string; lines: StatementLine[] }
  | { type: 'live'; updates: LiveUpdate[]; at: number };

export const initialState: LedgerState = { accounts: [], lastEntryIds: {}, statement: null, flashed: {} };

export function ledgerReducer(state: LedgerState, action: LedgerAction): LedgerState {
  switch (action.type) {
    case 'accounts-loaded':
      return { ...state, accounts: action.accounts };
    case 'account-opened':
      return { ...state, accounts: [action.account, ...state.accounts.filter((a) => a.id !== action.account.id)] };
    case 'statement-loaded': {
      const latest = action.lines[0]?.entryId;
      const lastEntryIds = latest === undefined ? state.lastEntryIds
        : { ...state.lastEntryIds, [action.accountId]: Math.max(latest, state.lastEntryIds[action.accountId] ?? 0) };
      return { ...state, statement: { accountId: action.accountId, lines: action.lines }, lastEntryIds };
    }
    case 'live':
      return applyLive(state, action.updates, action.at);
  }
}

function applyLive(state: LedgerState, updates: LiveUpdate[], at: number): LedgerState {
  const lastEntryIds = { ...state.lastEntryIds };
  const balances = new Map<string, number>();
  const flashed = { ...state.flashed };
  let statement = state.statement;

  for (const update of updates) {
    const seen = lastEntryIds[update.accountId] ?? 0;
    if (update.entryId > seen) {
      lastEntryIds[update.accountId] = update.entryId;
      balances.set(update.accountId, update.balanceAfter);
      flashed[update.accountId] = at;
    }
    if (statement && statement.accountId === update.accountId
      && !statement.lines.some((line) => line.entryId === update.entryId)) {
      const line: StatementLine = {
        entryId: update.entryId,
        transferId: update.transferId,
        transferType: update.transferType,
        counterpartyAccountId: update.counterpartyAccountId,
        amount: update.amount,
        currency: update.currency,
        balanceAfter: update.balanceAfter,
        postedAt: update.postedAt,
      };
      statement = {
        accountId: statement.accountId,
        lines: [line, ...statement.lines].sort((a, b) => b.entryId - a.entryId).slice(0, 50),
      };
    }
  }

  const accounts = balances.size === 0 ? state.accounts
    : state.accounts.map((account) => balances.has(account.id)
      ? { ...account, balance: balances.get(account.id) ?? account.balance }
      : account);

  return { ...state, accounts, lastEntryIds, statement, flashed };
}
