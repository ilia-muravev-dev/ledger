import { useState, type FormEvent } from 'react';
import { ApiError, api, type Account, type Problem } from '../api';
import { ProblemMessage } from './ProblemMessage';

export const CURRENCIES = ['EUR', 'USD', 'GBP', 'CHF', 'JPY'] as const;

export function OpenAccountForm({ onOpened }: { onOpened: (account: Account) => void }) {
  const [owner, setOwner] = useState('');
  const [currency, setCurrency] = useState<string>('EUR');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setProblem(null);
    try {
      onOpened(await api.openAccount(owner.trim(), currency));
      setOwner('');
    } catch (error) {
      setProblem(error instanceof ApiError ? error.problem : { type: 'network', title: 'Network error', status: 0 });
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <label className="field field--grow">
        <span>Owner</span>
        <input value={owner} onChange={(e) => setOwner(e.target.value)} placeholder="e.g. Alice" maxLength={200} required />
      </label>
      <label className="field">
        <span>Currency</span>
        <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
          {CURRENCIES.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </select>
      </label>
      <button type="submit" disabled={busy || owner.trim() === ''}>
        Open account
      </button>
      {problem && <ProblemMessage problem={problem} />}
    </form>
  );
}
