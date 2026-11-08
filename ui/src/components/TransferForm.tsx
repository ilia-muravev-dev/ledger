import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { ApiError, api, newIdempotencyKey, type Account, type Problem, type Transfer } from '../api';
import { formatMoney, parseToMinor, shortId } from '../money';
import { ProblemMessage } from './ProblemMessage';

type Result = { status: number; transfer: Transfer; key: string };

export function TransferForm({ accounts }: { accounts: Account[] }) {
  const active = useMemo(() => accounts.filter((a) => a.status === 'ACTIVE'), [accounts]);
  const [fromId, setFromId] = useState('');
  const [toId, setToId] = useState('');
  const [amount, setAmount] = useState('');
  const [key, setKey] = useState(newIdempotencyKey);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<Result | null>(null);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [rate, setRate] = useState<number | null>(null);

  const from = active.find((a) => a.id === fromId);
  const to = active.find((a) => a.id === toId);
  const parsed = from ? parseToMinor(amount, from.currency) : null;
  const isFx = Boolean(from && to && from.currency !== to.currency);

  useEffect(() => {
    if (!from || !to || from.currency === to.currency) {
      setRate(null);
      return;
    }
    let cancelled = false;
    api.fxRate(from.currency, to.currency)
      .then((quote) => !cancelled && setRate(quote.rate))
      .catch(() => !cancelled && setRate(null));
    return () => {
      cancelled = true;
    };
  }, [from?.currency, to?.currency]);

  const changed = (apply: () => void) => {
    apply();
    setKey(newIdempotencyKey());
    setResult(null);
    setProblem(null);
  };

  const send = async (idempotencyKey: string) => {
    if (!from || !to || !parsed?.ok) {
      return;
    }
    setBusy(true);
    setProblem(null);
    try {
      const outcome = await api.transfer({
        fromAccountId: from.id,
        toAccountId: to.id,
        amount: parsed.minor,
        currency: from.currency,
        ...(isFx ? { targetCurrency: to.currency } : {}),
      }, idempotencyKey);
      setResult({ status: outcome.status, transfer: outcome.body, key: idempotencyKey });
    } catch (error) {
      setResult(null);
      setProblem(error instanceof ApiError ? error.problem : { type: 'network', title: 'Network error', status: 0 });
    } finally {
      setBusy(false);
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void send(key);
  };

  const preview = isFx && rate !== null && parsed?.ok && to
    ? `≈ ${formatMoney(Math.round(parsed.minor * rate * 10 ** (digitsOf(to.currency) - digitsOf(from!.currency))), to.currency)} at ${rate}`
    : null;

  return (
    <form className="stack" onSubmit={submit}>
      <div className="grid-2">
        <label className="field">
          <span>From</span>
          <select value={fromId} onChange={(e) => changed(() => setFromId(e.target.value))} required>
            <option value="">Choose account</option>
            {active.map((a) => (
              <option key={a.id} value={a.id}>
                {a.owner} · {a.currency} · {formatMoney(a.balance, a.currency)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>To</span>
          <select value={toId} onChange={(e) => changed(() => setToId(e.target.value))} required>
            <option value="">Choose account</option>
            {active.filter((a) => a.id !== fromId).map((a) => (
              <option key={a.id} value={a.id}>
                {a.owner} · {a.currency} · {shortId(a.id)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="field">
        <span>Amount{from ? ` (${from.currency})` : ''}</span>
        <input
          inputMode="decimal"
          value={amount}
          onChange={(e) => changed(() => setAmount(e.target.value))}
          placeholder="0.00"
          aria-invalid={amount !== '' && parsed?.ok === false}
        />
        {amount !== '' && parsed && !parsed.ok && <small className="error">{parsed.error}</small>}
        {preview && <small className="muted">Converts to {to?.currency}: {preview}</small>}
      </label>
      <div className="row">
        <button type="submit" disabled={busy || !from || !to || !parsed?.ok}>
          {isFx ? 'Convert and send' : 'Send'}
        </button>
        {result && (
          <button type="button" className="secondary" disabled={busy} onClick={() => void send(result.key)}>
            Send again with the same key
          </button>
        )}
      </div>
      <p className="muted mono small">Idempotency-Key: {key}</p>
      {result && (
        <div className="notice notice--ok" role="status">
          <strong>{result.status === 201 ? '201 Created' : '200 OK — replayed, no money moved'}</strong>
          <p>
            {formatMoney(result.transfer.amount, result.transfer.currency)}
            {result.transfer.fx && ` → ${formatMoney(result.transfer.fx.targetAmount, result.transfer.fx.targetCurrency)} at ${result.transfer.fx.rate}`}
            {' · transfer '}
            <span className="mono">{shortId(result.transfer.id)}</span>
          </p>
        </div>
      )}
      {problem && <ProblemMessage problem={problem} />}
    </form>
  );
}

function digitsOf(currency: string): number {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
}
