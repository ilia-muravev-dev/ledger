import { useState, type FormEvent } from 'react';
import { ApiError, api, newIdempotencyKey, type Account, type Problem, type StatementLine } from '../api';
import { formatMoney, parseToMinor, shortId } from '../money';
import { ProblemMessage } from './ProblemMessage';

type Props = {
  account: Account;
  lines: StatementLine[] | null;
  accountsById: Map<string, Account>;
};

export function AccountDetail({ account, lines, accountsById }: Props) {
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem | null>(null);
  const parsed = parseToMinor(amount, account.currency);

  const deposit = async (event: FormEvent) => {
    event.preventDefault();
    if (!parsed.ok) {
      return;
    }
    setBusy(true);
    setProblem(null);
    try {
      await api.deposit(account.id, parsed.minor, account.currency, newIdempotencyKey());
      setAmount('');
    } catch (error) {
      setProblem(error instanceof ApiError ? error.problem : { type: 'network', title: 'Network error', status: 0 });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="stack">
      <header className="detail-header">
        <div>
          <h2>{account.owner}</h2>
          <p className="muted mono small">{account.id}</p>
        </div>
        <p className="balance">{formatMoney(account.balance, account.currency)}</p>
      </header>

      <form className="inline-form" onSubmit={deposit}>
        <label className="field field--grow">
          <span>Deposit ({account.currency})</span>
          <input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" />
        </label>
        <button type="submit" disabled={busy || !parsed.ok || account.status !== 'ACTIVE'}>
          Deposit
        </button>
        {problem && <ProblemMessage problem={problem} />}
      </form>

      <h3>Statement</h3>
      {lines === null && <p className="muted">Loading…</p>}
      {lines !== null && lines.length === 0 && <p className="empty">No entries yet.</p>}
      {lines !== null && lines.length > 0 && (
        <table className="statement">
          <thead>
            <tr>
              <th scope="col">When</th>
              <th scope="col">Counterparty</th>
              <th scope="col" className="num">
                Amount
              </th>
              <th scope="col" className="num">
                Balance
              </th>
            </tr>
          </thead>
          <tbody>
            {lines.map((line) => (
              <tr key={line.entryId}>
                <td className="muted small">{new Date(line.postedAt).toLocaleTimeString()}</td>
                <td>{describeCounterparty(line, accountsById)}</td>
                <td className={`num ${line.amount < 0 ? 'debit' : 'credit'}`}>
                  {formatMoney(line.amount, line.currency, { signed: true })}
                </td>
                <td className="num">{formatMoney(line.balanceAfter, line.currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function describeCounterparty(line: StatementLine, accountsById: Map<string, Account>): string {
  if (line.transferType === 'DEPOSIT') {
    return 'Deposit';
  }
  const counterparty = accountsById.get(line.counterpartyAccountId);
  return counterparty ? `${counterparty.owner} (${counterparty.currency})` : shortId(line.counterpartyAccountId);
}
