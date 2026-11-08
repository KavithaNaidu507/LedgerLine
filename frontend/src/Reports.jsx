import { useCallback, useEffect, useState } from 'react';
import { api, get } from './api';
import { formatMoney } from './money';

export const when = (t) => new Date(t).toLocaleString();

export function useLoad(path) {
  const [state, setState] = useState({ data: null, error: '', loading: true });
  const reload = useCallback(() => {
    setState((s) => ({ ...s, loading: true }));
    get(path).then((data) => setState({ data, error: '', loading: false }))
      .catch((e) => setState({ data: null, error: e.message, loading: false }));
  }, [path]);
  useEffect(reload, [reload]);
  return { ...state, reload };
}

export function Table({ columns, rows, empty = 'Nothing to show yet.' }) {
  if (!rows) return null;
  if (rows.length === 0) return <p className="muted">{empty}</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr>{columns.map((c) => <th key={c.h}>{c.h}</th>)}</tr></thead>
        <tbody>{rows.map((r) => <tr key={r.id ?? r.accountNumber}>{columns.map((c) => <td key={c.h} className={c.cls}>{c.v(r)}</td>)}</tr>)}</tbody>
      </table>
    </div>
  );
}

const txCols = [
  { h: 'ID', v: (t) => '#' + t.id }, { h: 'Time', v: (t) => when(t.createdAt) }, { h: 'Type', v: (t) => <span className={'pill ' + t.type}>{t.type}</span> },
  { h: 'From', v: (t) => t.fromAccount }, { h: 'To', v: (t) => t.toAccount },
  { h: 'Amount', v: (t) => formatMoney(t.amount), cls: 'num' }, { h: 'Note', v: (t) => t.description || '' },
];
export const ledgerCols = [
  { h: 'Entry', v: (e) => '#' + e.id }, { h: 'Tx', v: (e) => '#' + e.transactionId }, { h: 'Time', v: (e) => when(e.createdAt) },
  { h: 'Account', v: (e) => e.accountNumber }, { h: 'Side', v: (e) => <span className={'pill ' + e.direction}>{e.direction}</span> },
  { h: 'Amount', v: (e) => formatMoney(e.amount), cls: 'num' }, { h: 'Balance after', v: (e) => formatMoney(e.balanceAfter), cls: 'num' },
];
export { txCols };

function Section({ title, load, children }) {
  return (
    <section className="card">
      <div className="row between"><h2>{title}</h2><button className="btn small ghost" onClick={load.reload}>Refresh</button></div>
      {load.error && <div className="alert err">{load.error}</div>}
      {load.loading && !load.data ? <p className="muted">Loading…</p> : children}
    </section>
  );
}

export const TransactionsView = () => { const l = useLoad('/transactions'); return <Section title="Transactions" load={l}><Table columns={txCols} rows={l.data} /></Section>; };
export const LedgerView = () => { const l = useLoad('/ledger'); return <Section title="Ledger (latest 200 entries, read-only)" load={l}><Table columns={ledgerCols} rows={l.data} /></Section>; };

export function AuditView() {
  const l = useLoad('/audit-logs');
  const cols = [{ h: 'ID', v: (a) => '#' + a.id }, { h: 'Time', v: (a) => when(a.createdAt) }, { h: 'User', v: (a) => a.userId ?? '—' },
    { h: 'Action', v: (a) => a.action }, { h: 'Entity', v: (a) => (a.entityType ? `${a.entityType} ${a.entityId ?? ''}` : '') }, { h: 'Details', v: (a) => a.details || '' }];
  return <Section title="Audit log (latest 200)" load={l}><Table columns={cols} rows={l.data} /></Section>;
}

export function IntegrityView() {
  const l = useLoad('/reports/integrity');
  const r = l.data;
  const Ok = ({ ok }) => <span className={'pill ' + (ok ? 'CREDIT' : 'DEBIT')}>{ok ? 'PASS' : 'FAIL'}</span>;
  return (
    <Section title="Integrity report" load={l}>
      {r && (
        <>
          <div className="stats">
            <div><span className="muted">Total debits</span><b>{formatMoney(r.totalDebits)}</b></div>
            <div><span className="muted">Total credits</span><b>{formatMoney(r.totalCredits)}</b></div>
            <div><span className="muted">Customer funds</span><b>{formatMoney(r.customerFunds)}</b></div>
            <div><span className="muted">Sum of ALL balances</span><b>{formatMoney(r.sumOfAllBalances)}</b></div>
          </div>
          <p><Ok ok={r.debitsEqualCredits} /> Debits equal credits &nbsp; <Ok ok={r.moneyConserved} /> Money conserved (all balances sum to zero) &nbsp; <Ok ok={r.mismatches.length === 0} /> Every balance matches its ledger</p>
          <p className="muted">{r.accountCount} accounts · {r.entryCount} ledger entries</p>
          {r.mismatches.map((m) => <div className="alert err" key={m}>{m}</div>)}
        </>
      )}
    </Section>
  );
}

export function AccountsView({ role }) {
  const l = useLoad('/accounts');
  const [msg, setMsg] = useState('');
  async function setStatus(a, status) {
    setMsg('');
    try { await api(`/accounts/${a.id}/status`, { method: 'PATCH', body: { status } }); l.reload(); } catch (e) { setMsg(e.message); }
  }
  const cols = [{ h: 'Number', v: (a) => a.accountNumber }, { h: 'Name', v: (a) => a.name }, { h: 'Owner', v: (a) => a.ownerName || 'System' },
    { h: 'Balance', v: (a) => formatMoney(a.balance), cls: 'num' }, { h: 'Status', v: (a) => a.status },
    ...(role === 'ADMIN' ? [{ h: 'Action', v: (a) => a.external ? '' : <button className="btn small ghost" onClick={() => setStatus(a, a.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE')}>{a.status === 'ACTIVE' ? 'Freeze' : 'Unfreeze'}</button> }] : [])];
  return <Section title="All accounts" load={l}>{msg && <div className="alert err">{msg}</div>}<Table columns={cols} rows={l.data} /></Section>;
}

export function UsersView({ me }) {
  const l = useLoad('/admin/users');
  const [msg, setMsg] = useState('');
  async function setRole(u, role) {
    setMsg('');
    try { await api(`/admin/users/${u.id}/role`, { method: 'PATCH', body: { role } }); l.reload(); } catch (e) { setMsg(e.message); }
  }
  const cols = [{ h: 'ID', v: (u) => u.id }, { h: 'Name', v: (u) => u.fullName }, { h: 'Email', v: (u) => u.email }, { h: 'Joined', v: (u) => when(u.createdAt) },
    { h: 'Role', v: (u) => u.id === me.id ? u.role : (
      <select value={u.role} onChange={(e) => setRole(u, e.target.value)}>
        <option>ACCOUNT_HOLDER</option><option>AUDITOR</option><option>ADMIN</option>
      </select>) }];
  return <Section title="Users" load={l}>{msg && <div className="alert err">{msg}</div>}<Table columns={cols} rows={l.data} /></Section>;
}
