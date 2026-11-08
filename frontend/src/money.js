// Display/validation helpers only. The backend re-validates everything and owns all real balances.
export const isValidAmount = (s) => /^\d{1,13}(\.\d{1,2})?$/.test(s) && Number(s) > 0;

export const formatMoney = (v) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(Number(v));
