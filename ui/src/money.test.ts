import { describe, expect, it } from 'vitest';
import { formatMoney, minorDigits, parseToMinor, toDecimalString } from './money';

describe('minorDigits', () => {
  it('knows the ISO 4217 minor units', () => {
    expect(minorDigits('EUR')).toBe(2);
    expect(minorDigits('JPY')).toBe(0);
    expect(minorDigits('KWD')).toBe(3);
  });
});

describe('toDecimalString', () => {
  it('places the decimal point without floating point maths', () => {
    expect(toDecimalString(123456, 'EUR')).toBe('1234.56');
    expect(toDecimalString(5, 'USD')).toBe('0.05');
    expect(toDecimalString(-250, 'EUR')).toBe('-2.50');
    expect(toDecimalString(1500, 'JPY')).toBe('1500');
    expect(toDecimalString(1, 'KWD')).toBe('0.001');
    expect(toDecimalString(9007199254740991, 'EUR')).toBe('90071992547409.91');
  });
});

describe('parseToMinor', () => {
  it('converts user input to integer minor units', () => {
    expect(parseToMinor('12.34', 'EUR')).toEqual({ ok: true, minor: 1234 });
    expect(parseToMinor('12', 'EUR')).toEqual({ ok: true, minor: 1200 });
    expect(parseToMinor('12.3', 'EUR')).toEqual({ ok: true, minor: 1230 });
    expect(parseToMinor('0,10', 'EUR')).toEqual({ ok: true, minor: 10 });
    expect(parseToMinor(' 1 000 ', 'JPY')).toEqual({ ok: true, minor: 1000 });
    expect(parseToMinor('0.1', 'EUR')).toEqual({ ok: true, minor: 10 });
    expect(parseToMinor('1.005', 'KWD')).toEqual({ ok: true, minor: 1005 });
  });

  it('never goes through a binary float', () => {
    expect(parseToMinor('0.29', 'EUR')).toEqual({ ok: true, minor: 29 });
    expect(parseToMinor('1.15', 'EUR')).toEqual({ ok: true, minor: 115 });
  });

  it('rejects input that is not a positive amount in the currency', () => {
    expect(parseToMinor('', 'EUR').ok).toBe(false);
    expect(parseToMinor('abc', 'EUR').ok).toBe(false);
    expect(parseToMinor('-5', 'EUR').ok).toBe(false);
    expect(parseToMinor('0', 'EUR').ok).toBe(false);
    expect(parseToMinor('1.234', 'EUR').ok).toBe(false);
    expect(parseToMinor('1.5', 'JPY').ok).toBe(false);
    expect(parseToMinor('99999999999999999', 'EUR').ok).toBe(false);
  });
});

describe('formatMoney', () => {
  it('formats with the currency and an optional sign', () => {
    expect(formatMoney(123456, 'EUR')).toContain('1,234.56');
    expect(formatMoney(250, 'USD', { signed: true })).toMatch(/^\+/);
    expect(formatMoney(1500, 'JPY')).toContain('1,500');
  });
});
