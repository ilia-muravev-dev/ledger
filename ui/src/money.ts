const digitsCache = new Map<string, number>();

export function minorDigits(currency: string): number {
  const cached = digitsCache.get(currency);
  if (cached !== undefined) {
    return cached;
  }
  const digits = new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
    .maximumFractionDigits ?? 2;
  digitsCache.set(currency, digits);
  return digits;
}

export function toDecimalString(minor: number, currency: string): string {
  const digits = minorDigits(currency);
  const negative = minor < 0;
  const raw = Math.abs(minor).toString();
  if (digits === 0) {
    return (negative ? '-' : '') + raw;
  }
  const padded = raw.padStart(digits + 1, '0');
  const integer = padded.slice(0, padded.length - digits);
  const fraction = padded.slice(padded.length - digits);
  return `${negative ? '-' : ''}${integer}.${fraction}`;
}

export function formatMoney(minor: number, currency: string, options: { signed?: boolean } = {}): string {
  const formatter = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    signDisplay: options.signed ? 'exceptZero' : 'auto',
  });
  return formatter.format(toDecimalString(minor, currency) as Intl.StringNumericLiteral);
}

export type ParseResult = { ok: true; minor: number } | { ok: false; error: string };

export function parseToMinor(input: string, currency: string): ParseResult {
  const text = input.trim().replace(/[\s_]/g, '');
  if (text === '') {
    return { ok: false, error: 'Enter an amount' };
  }
  const match = /^(\d+)(?:[.,](\d*))?$/.exec(text);
  if (!match) {
    return { ok: false, error: 'Use digits and an optional decimal point' };
  }
  const digits = minorDigits(currency);
  const integer = match[1] ?? '';
  const fraction = match[2] ?? '';
  if (fraction.length > digits) {
    return {
      ok: false,
      error: digits === 0 ? `${currency} has no minor units` : `${currency} allows ${digits} decimal places`,
    };
  }
  const minor = Number(integer + fraction.padEnd(digits, '0'));
  if (!Number.isSafeInteger(minor)) {
    return { ok: false, error: 'Amount is too large' };
  }
  if (minor <= 0) {
    return { ok: false, error: 'Amount must be greater than zero' };
  }
  return { ok: true, minor };
}

export function shortId(id: string): string {
  return id.slice(0, 8);
}
