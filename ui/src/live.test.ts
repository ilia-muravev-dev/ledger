import { describe, expect, it } from 'vitest';
import { subscriptionMessage } from './live';

describe('subscriptionMessage', () => {
  it('sends each account once in a stable order', () => {
    expect(JSON.parse(subscriptionMessage(['b', 'a', 'b']))).toEqual({ type: 'subscribe', accountIds: ['a', 'b'] });
  });

  it('is identical for the same set in any order, so the hook does not resubscribe needlessly', () => {
    expect(subscriptionMessage(['x', 'y'])).toBe(subscriptionMessage(['y', 'x']));
  });

  it('unsubscribes with an empty list', () => {
    expect(JSON.parse(subscriptionMessage([]))).toEqual({ type: 'subscribe', accountIds: [] });
  });
});
