import { useCallback, useEffect, useState } from 'react';
import { api, get } from './api';
import { formatMoney, isValidAmount } from './money';
import { Table, txCols, ledgerCols, useLoad, when } from './Reports';

/**
 * Each form owns an Idempotency-Key (a random UUID). The key is only replaced after the server gave a definite
 * answer (success or a 4xx rejection). If the network drops or the server errors (5xx), the SAME key is reused on
 * retry, so clicking "Submit" again can never pay twice.
 */
function useIdempotentSubmit(run) {
  const [key, setKey] = useState(() => crypto.randomUUID());
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);
  async function submit(e) {
    e.preventDefault();
    setBusy(true); setMsg(null);
    try {
      const { data, replayed } = await run(key);
      setMsg({ ok: replayed ? 'Duplicate request detected: the original result was returned and nothing was charged twice.' : `Done. Transaction #${data.id} completed.` });
      setKey(crypto.randomUUID());
      return true;
    } catch (err) {
      setMsg({ err: err.message });
      if (err.status && err.status < 500) setKey(crypto.randomUUID());
      return false;
    } finally { setBusy(false); }
  }
  return { submit, msg, busy, setMsg };
}

function Msg({ msg }) {
  if (!msg) return null;
  return <div className={'alert ' + (msg.err ? 'err' : 'ok')}>{msg.err || msg.ok}</div>;
}

function MoneyForm({ kind, account, onDone }) {
  const [amount, setAmount] = useState('');
  const [desc, setDesc] = useState('');
  const { submit, msg, busy, setMsg } = useIdempotentSubmit(async (key) => {
    if (!isValidAmount(amount)) throw new Error('Enter a positive amount with at most 2 decimals');
    const res = await api(`/accounts/${account.id}/${kind}`, { method: 'POST', body: { amount, description: desc }, headers: { 'Idempotency-Key': key } });
    return res;
  });
  async function onSubmit(e) {
    if (await submit(e)) { setAmount(''); setDesc(''); onDone(); }
  }
  return (
    <form className="card" onSubmit={onSubmit}>
      <h3>{kind === 'deposit' ? 'Deposit' : 'Withdraw'}</h3>
      <label>Amount (₹)<input inputMode="decimal" value={amount} onChange={(e) => { setAmount(e.target.value); setMsg(null); }} placeholder="0.00" /></label>
      <label>Note<input value={desc} maxLength={200} onChange={(e) => setDesc(e.target.value)} /></label>
      <Msg msg={msg} />
      <button className="btn" disabled={busy || account.status !== 'ACTIVE'}>{busy ? 'Working…' : kind === 'deposit' ? 'Deposit' : 'Withdraw'}</button>
    </form>
  );
}

function TransferForm({ account, onDone }) {
  const [to, setTo] = useState('');
  const [amount, setAmount] = useState('');
  const [desc, setDesc] = useState('');
  const { submit, msg, busy, setMsg } = useIdempotentSubmit(async (key) => {
    if (!to.trim()) throw new Error('Enter the destination account number');
    if (!isValidAmount(amount)) throw new Error('Enter a positive amount with at most 2 decimals');
    return api('/transfers', { method: 'POST', body: { fromAccountId: account.id, toAccountNumber: to.trim(), amount, description: desc }, headers: { 'Idempotency-Key': key } });
  });
  async function onSubmit(e) {
    if (await submit(e)) { setTo(''); setAmount(''); setDesc(''); onDone(); }
  }
  return (
    <form className="card" onSubmit={onSubmit}>
      <h3>Transfer from {account.accountNumber}</h3>
      <label>To account number<input value={to} onChange={(e) => { setTo(e.target.value); setMsg(null); }} placeholder="LL0123456789" /></label>
      <label>Amount (₹)<input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" /></label>
      <label>Note<input value={desc} maxLength={200} onChange={(e) => setDesc(e.target.value)} /></label>
      <Msg msg={msg} />
      <button className="btn" disabled={busy || account.status !== 'ACTIVE'}>{busy ? 'Working…' : 'Send money'}</button>
    </form>
  );
}

function History({ id }) { const l = useLoad(`/accounts/${id}/transactions`); return l.error ? <div className="alert err">{l.error}</div> : <Table columns={txCols} rows={l.data} empty="No transactions yet." />; }
function Ledger({ id }) { const l = useLoad(`/accounts/${id}/ledger`); return l.error ? <div className="alert err">{l.error}</div> : <Table columns={ledgerCols} rows={l.data} empty="No ledger entries yet." />; }

function Statement({ id }) {
  const today = new Date().toISOString().slice(0, 10);
  const monthAgo = new Date(Date.now() - 30 * 864e5).toISOString().slice(0, 10);
  const [from, setFrom] = useState(monthAgo);
  const [to, setTo] = useState(today);
  const [st, setSt] = useState(null);
  const [error, setError] = useState('');
  const run = useCallback(() => {
    get(`/accounts/${id}/statement?from=${from}&to=${to}`).then((d) => { setSt(d); setError(''); }).catch((e) => setError(e.message));
  }, [id, from, to]);
  useEffect(run, [run]);
  return (
    <div>
      <div className="row">
        <label>From<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label>To<input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
      </div>
      {error && <div className="alert err">{error}</div>}
      {st && (
        <>
          <div className="stats">
            <div><span className="muted">Opening</span><b>{formatMoney(st.openingBalance)}</b></div>
            <div><span className="muted">Credits in</span><b>{formatMoney(st.totalCredits)}</b></div>
            <div><span className="muted">Debits out</span><b>{formatMoney(st.totalDebits)}</b></div>
            <div><span className="muted">Closing</span><b>{formatMoney(st.closingBalance)}</b></div>
          </div>
          <Table columns={ledgerCols} rows={st.entries} empty="No entries in this period." />
        </>
      )}
    </div>
  );
}

function AccountDetail({ account }) {
  const [tab, setTab] = useState('history');
  return (
    <section className="card">
      <div className="tabs">
        {[['history', 'History'], ['ledger', 'Ledger'], ['statement', 'Statement']].map(([k, label]) =>
          <button key={k} className={tab === k ? 'tab active' : 'tab'} onClick={() => setTab(k)}>{label}</button>)}
      </div>
      {tab === 'history' && <History id={account.id} />}
      {tab === 'ledger' && <Ledger id={account.id} />}
      {tab === 'statement' && <Statement id={account.id} />}
    </section>
  );
}

export default function Dashboard() {
  const [accounts, setAccounts] = useState(null);
  const [selId, setSelId] = useState(null);
  const [version, setVersion] = useState(0);
  const [error, setError] = useState('');
  const [name, setName] = useState('');

  const load = useCallback(() => {
    get('/accounts').then((a) => { setAccounts(a); setSelId((id) => (a.some((x) => x.id === id) ? id : a[0]?.id ?? null)); setError(''); })
      .catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);
  const refresh = () => { load(); setVersion((v) => v + 1); };

  async function createAccount(e) {
    e.preventDefault();
    try {
      const { data } = await api('/accounts', { method: 'POST', body: { name } });
      setName(''); await load(); setSelId(data.id);
    } catch (err) { setError(err.message); }
  }

  const selected = accounts?.find((a) => a.id === selId);
  const total = accounts?.reduce((s, a) => s + Number(a.balance), 0) ?? 0;

  return (
    <>
      {error && <div className="alert err">{error}</div>}
      <section className="card">
        <div className="row between">
          <h2>Your accounts</h2>
          <span className="muted">Total balance <b className="big">{formatMoney(total)}</b></span>
        </div>
        {accounts && accounts.length === 0 && <p className="muted">You have no accounts yet. Create your first one below.</p>}
        <div className="acc-grid">
          {accounts?.map((a) => (
            <button key={a.id} className={'acc' + (a.id === selId ? ' sel' : '')} onClick={() => setSelId(a.id)}>
              <span className="muted">{a.name}{a.status !== 'ACTIVE' ? ' · FROZEN' : ''}</span>
              <b>{formatMoney(a.balance)}</b>
              <small>{a.accountNumber}</small>
            </button>
          ))}
        </div>
        <form className="row" onSubmit={createAccount}>
          <input placeholder="New account name (e.g. Savings)" value={name} onChange={(e) => setName(e.target.value)} required maxLength={60} />
          <button className="btn small">Create account</button>
        </form>
      </section>

      {selected && (
        <>
          <div className="grid3">
            <MoneyForm key={'d' + selected.id} kind="deposit" account={selected} onDone={refresh} />
            <MoneyForm key={'w' + selected.id} kind="withdraw" account={selected} onDone={refresh} />
            <TransferForm key={'t' + selected.id} account={selected} onDone={refresh} />
          </div>
          <AccountDetail key={selected.id + ':' + version} account={selected} />
        </>
      )}
    </>
  );
}
