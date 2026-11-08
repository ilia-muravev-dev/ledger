import type { Account } from '../api';
import { formatMoney, shortId } from '../money';

type Props = {
  accounts: Account[];
  selectedId: string | null;
  flashed: Record<string, number>;
  onSelect: (id: string) => void;
};

export function AccountsTable({ accounts, selectedId, flashed, onSelect }: Props) {
  if (accounts.length === 0) {
    return <p className="empty">No accounts yet. Open one to get started.</p>;
  }
  return (
    <table className="accounts">
      <thead>
        <tr>
          <th scope="col">Owner</th>
          <th scope="col">Account</th>
          <th scope="col" className="num">
            Balance
          </th>
        </tr>
      </thead>
      <tbody>
        {accounts.map((account) => (
          <tr
            key={account.id}
            className={account.id === selectedId ? 'is-selected' : undefined}
            onClick={() => onSelect(account.id)}
          >
            <td>
              <button type="button" className="link" onClick={() => onSelect(account.id)}>
                {account.owner}
              </button>
              {account.status === 'CLOSED' && <span className="tag">closed</span>}
            </td>
            <td className="mono muted">
              {shortId(account.id)} · {account.currency}
            </td>
            <td className="num">
              <span key={flashed[account.id] ?? 0} className={flashed[account.id] ? 'flash' : undefined}>
                {formatMoney(account.balance, account.currency)}
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
