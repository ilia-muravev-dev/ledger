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

export function useLiveUpdates(onUpdates: (updates: LiveUpdate[]) => void): LiveStatus {
  const [status, setStatus] = useState<LiveStatus>('connecting');
  const handler = useRef(onUpdates);

  useEffect(() => {
    handler.current = onUpdates;
  }, [onUpdates]);

  useEffect(() => {
    let socket: WebSocket | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let stopped = false;

    const connect = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
      socket = new WebSocket(`${protocol}://${window.location.host}/ws/updates`);
      setStatus('connecting');
      socket.onopen = () => {
        attempt = 0;
        setStatus('live');
      };
      socket.onmessage = (message) => {
        handler.current(JSON.parse(message.data) as LiveUpdate[]);
      };
      socket.onclose = () => {
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
