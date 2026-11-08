import { useState } from 'react';
import { api, setToken } from './api';

export default function Auth({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const body = mode === 'login' ? { email: form.email, password: form.password } : form;
      const { data } = await api(`/auth/${mode}`, { method: 'POST', body });
      setToken(data.token);
      onLogin(data.user);
    } catch (err) {
      const detail = err.fields ? ' ' + Object.values(err.fields).join(', ') : '';
      setError(err.message + detail);
    } finally { setBusy(false); }
  }

  return (
    <div className="auth-wrap">
      <form className="card auth" onSubmit={submit}>
        <h1>LedgerLine</h1>
        <p className="muted">Money movement with a ledger you can prove.</p>
        {mode === 'register' && (
          <label>Full name<input value={form.fullName} onChange={set('fullName')} required maxLength={100} /></label>
        )}
        <label>Email<input type="email" value={form.email} onChange={set('email')} required autoComplete="username" /></label>
        <label>Password<input type="password" value={form.password} onChange={set('password')} required minLength={mode === 'register' ? 8 : 1}
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
          {mode === 'register' && <small className="muted">At least 8 characters.</small>}
        </label>
        {error && <div className="alert err">{error}</div>}
        <button className="btn" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
        <button type="button" className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>
          {mode === 'login' ? 'New here? Register' : 'Have an account? Sign in'}
        </button>
      </form>
    </div>
  );
}
