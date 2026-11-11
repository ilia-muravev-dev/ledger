import { useEffect, useRef, useState } from 'react';

export type LiveUpdate = {
  type: 'entry-posted';
  accountId: string;
  entryId: number;
  transferId: string;
  transferType: string;
  counterpartyAccountId: string;
  amount: number;
  currency: string;
  balanceAfter: number;
  postedAt: string;
};

export type LiveStatus = 'connecting' | 'live' | 'offline';

export function subscriptionMessage(accountIds: readonly string[]): string {
  return JSON.stringify({ type: 'subscribe', accountIds: [...new Set(accountIds)].sort() });
}

export function useLiveUpdates(accountIds: readonly string[], onUpdates: (updates: LiveUpdate[]) => void): LiveStatus {
  const [status, setStatus] = useState<LiveStatus>('connecting');
  const handler = useRef(onUpdates);
  const socketRef = useRef<WebSocket | undefined>(undefined);
  const message = subscriptionMessage(accountIds);
  const subscription = useRef(message);

  useEffect(() => {
    handler.current = onUpdates;
  }, [onUpdates]);

  useEffect(() => {
    subscription.current = message;
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(message);
    }
  }, [message]);

  useEffect(() => {
    let socket: WebSocket | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let stopped = false;

    const connect = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
      const current = new WebSocket(`${protocol}://${window.location.host}/ws/updates`);
      socket = current;
      socketRef.current = current;
      setStatus('connecting');
      current.onopen = () => {
        attempt = 0;
        current.send(subscription.current);
        setStatus('live');
      };
      current.onmessage = (event) => {
        const data: unknown = JSON.parse(event.data);
        if (Array.isArray(data)) {
          handler.current(data as LiveUpdate[]);
        }
      };
      current.onclose = () => {
        if (stopped) {
          return;
        }
        setStatus('offline');
        attempt += 1;
        retry = setTimeout(connect, Math.min(1000 * 2 ** attempt, 15000));
      };
    };

    connect();
    return () => {
      stopped = true;
      clearTimeout(retry);
      socket?.close();
    };
  }, []);

  return status;
}
