// Tiny fetch wrapper. The JWT lives in sessionStorage (cleared when the tab closes).
const KEY = 'ledgerline_token';
export const getToken = () => sessionStorage.getItem(KEY);
export const setToken = (t) => (t ? sessionStorage.setItem(KEY, t) : sessionStorage.removeItem(KEY));

/** Returns { data, replayed }. Throws Error with .status and .code on failure. */
export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const token = getToken();
  const res = await fetch('/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* non-JSON body */ }
  if (!res.ok) {
    const err = new Error(data?.message || (res.status === 401 ? 'Please sign in again' : `Request failed (${res.status})`));
    err.status = res.status;
    err.code = data?.code;
    err.fields = data?.fieldErrors;
    throw err;
  }
  return { data, replayed: res.headers.get('Idempotent-Replay') === 'true' };
}

export const get = (path) => api(path).then((r) => r.data);
