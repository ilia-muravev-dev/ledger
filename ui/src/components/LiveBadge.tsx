import type { LiveStatus } from '../live';

const LABELS: Record<LiveStatus, string> = {
  connecting: 'Connecting…',
  live: 'Live',
  offline: 'Offline — retrying',
};

export function LiveBadge({ status }: { status: LiveStatus }) {
  return (
    <span className={`live-badge live-badge--${status}`} role="status" aria-live="polite">
      <span className="live-badge__dot" aria-hidden="true" />
      {LABELS[status]}
    </span>
  );
}
