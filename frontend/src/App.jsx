import { useEffect, useState } from 'react';
import { get, getToken, setToken } from './api';
import Auth from './Auth';
import Dashboard from './Dashboard';
import { AccountsView, AuditView, IntegrityView, LedgerView, TransactionsView, UsersView } from './Reports';

const TABS = {
  ACCOUNT_HOLDER: [['dashboard', 'Dashboard'], ['transactions', 'Transactions'], ['profile', 'Profile']],
  ADMIN: [['integrity', 'Overview'], ['users', 'Users'], ['accounts', 'Accounts'], ['transactions', 'Transactions'], ['ledger', 'Ledger'], ['audit', 'Audit log'], ['profile', 'Profile']],
  AUDITOR: [['integrity', 'Overview'], ['accounts', 'Accounts'], ['transactions', 'Transactions'], ['ledger', 'Ledger'], ['audit', 'Audit log'], ['profile', 'Profile']],
};

function Profile({ user }) {
  return (
    <section className="card">
      <h2>Profile</h2>
      <dl className="profile">
        <dt>Name</dt><dd>{user.fullName}</dd>
        <dt>Email</dt><dd>{user.email}</dd>
        <dt>Role</dt><dd>{user.role}</dd>
        <dt>Member since</dt><dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
      </dl>
      {user.role === 'AUDITOR' && <p className="muted">Auditors have read-only access: every write request is refused by the server.</p>}
    </section>
  );
}

export default function App() {
  const [user, setUser] = useState(null);
  const [booting, setBooting] = useState(!!getToken());
  const [tab, setTab] = useState(null);

  useEffect(() => {
    if (!getToken()) return;
    get('/me').then(setUser).catch(() => setToken(null)).finally(() => setBooting(false));
  }, []);

  const logout = () => { setToken(null); setUser(null); setTab(null); };
  if (booting) return <div className="auth-wrap"><p className="muted">Loading…</p></div>;
  if (!user) return <Auth onLogin={(u) => { setUser(u); setTab(null); }} />;

  const tabs = TABS[user.role];
  const current = tab && tabs.some(([k]) => k === tab) ? tab : tabs[0][0];
  const views = {
    dashboard: <Dashboard />, transactions: <TransactionsView />, profile: <Profile user={user} />,
    integrity: <IntegrityView />, users: <UsersView me={user} />, accounts: <AccountsView role={user.role} />,
    ledger: <LedgerView />, audit: <AuditView />,
  };

  return (
    <>
      <header className="top">
        <div className="brand">LedgerLine</div>
        <nav>{tabs.map(([k, label]) => <button key={k} className={k === current ? 'nav active' : 'nav'} onClick={() => setTab(k)}>{label}</button>)}</nav>
        <div className="who"><span>{user.fullName} <small className="pill">{user.role}</small></span><button className="btn small ghost light" onClick={logout}>Log out</button></div>
      </header>
      <main>{views[current]}</main>
    </>
  );
}
