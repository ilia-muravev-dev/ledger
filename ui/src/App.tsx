import { useCallback, useEffect, useMemo, useReducer, useState } from 'react';
import { ApiError, api, type Account } from './api';
import { AccountDetail } from './components/AccountDetail';
import { AccountsTable } from './components/AccountsTable';
import { LiveBadge } from './components/LiveBadge';
import { OpenAccountForm } from './components/OpenAccountForm';
import { TransferForm } from './components/TransferForm';
import { initialState, ledgerReducer } from './ledgerState';
import { useLiveUpdates, type LiveUpdate } from './live';

export function App() {
  const [state, dispatch] = useReducer(ledgerReducer, initialState);
  const [selectedId, setSelectedId] = useState<string | null>(
    () => new URLSearchParams(window.location.search).get('account'),
  );
  const [loadError, setLoadError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      dispatch({ type: 'accounts-loaded', accounts: await api.listAccounts() });
      setLoadError(null);
    } catch (error) {
      setLoadError(error instanceof ApiError ? error.message : 'The ledger service is not reachable.');
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    if (!selectedId) {
      return;
    }
    let cancelled = false;
    api.statement(selectedId)
      .then((statement) => !cancelled && dispatch({ type: 'statement-loaded', accountId: selectedId, lines: statement.items }))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  const onUpdates = useCallback((updates: LiveUpdate[]) => {
    dispatch({ type: 'live', updates, at: Date.now() });
  }, []);
  const status = useLiveUpdates(onUpdates);

  useEffect(() => {
    const url = new URL(window.location.href);
    if (selectedId) {
      url.searchParams.set('account', selectedId);
    } else {
      url.searchParams.delete('account');
    }
    window.history.replaceState(null, '', url);
  }, [selectedId]);

  const accountsById = useMemo(() => new Map(state.accounts.map((a) => [a.id, a])), [state.accounts]);
  const selected = selectedId ? accountsById.get(selectedId) : undefined;
  const lines = state.statement?.accountId === selectedId ? state.statement.lines : null;

  const opened = (account: Account) => {
    dispatch({ type: 'account-opened', account });
    setSelectedId(account.id);
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand__mark" aria-hidden="true">≡</span>
          <h1>Ledger</h1>
          <span className="muted small">double-entry payments demo</span>
        </div>
        <LiveBadge status={status} />
      </header>

      {loadError && (
        <div className="notice notice--error" role="alert">
          <strong>Could not load accounts</strong>
          <p>{loadError}</p>
          <button type="button" className="secondary" onClick={() => void reload()}>
            Retry
          </button>
        </div>
      )}

      <main className="layout">
        <section className="card">
          <h2>Accounts</h2>
          <OpenAccountForm onOpened={opened} />
          <AccountsTable accounts={state.accounts} selectedId={selectedId} flashed={state.flashed} onSelect={setSelectedId} />
        </section>

        <section className="card">
          <h2>New transfer</h2>
          <TransferForm accounts={state.accounts} />
        </section>

        <section className="card card--wide">
          {selected ? (
            <AccountDetail account={selected} lines={lines} accountsById={accountsById} />
          ) : (
            <p className="empty">Select an account to see its statement and make a deposit.</p>
          )}
        </section>
      </main>
    </div>
  );
}
