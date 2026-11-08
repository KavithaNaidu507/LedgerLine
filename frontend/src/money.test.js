import { describe, expect, it } from 'vitest';
import { formatMoney, isValidAmount } from './money';

describe('isValidAmount', () => {
  it('accepts positive amounts with up to 2 decimals', () => {
    expect(isValidAmount('10')).toBe(true);
    expect(isValidAmount('10.5')).toBe(true);
    expect(isValidAmount('0.01')).toBe(true);
  });
  it('rejects zero, negatives, >2 decimals and junk', () => {
    for (const bad of ['0', '0.00', '-5', '1.234', 'abc', '', '1e3', ' 5'])
      expect(isValidAmount(bad)).toBe(false);
  });
});

describe('formatMoney', () => {
  it('formats with grouping and two decimals', () => {
    expect(formatMoney('1234.5')).toContain('1,234.50');
  });
});
