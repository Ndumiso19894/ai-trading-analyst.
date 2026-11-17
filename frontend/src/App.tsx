import { useEffect, useState } from 'react'

const NAV = [
  'Dashboard',
  'Market Overview',
  'Trading Chart',
  'Automatic Analysis',
  'Screenshot Analysis',
  'Trading Signals',
  'Multi-Timeframe Analysis',
  'Technical Indicators',
  'Support and Resistance',
  'Trade Calculator',
  'Backtesting',
  'Signal History',
  'Performance Reports',
  'Settings',
] as const

type Health = { status: string; app: string; version: string } | null

export default function App() {
  const [active, setActive] = useState<string>('Dashboard')
  const [health, setHealth] = useState<Health>(null)
  const [healthError, setHealthError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/health')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then(setHealth)
      .catch((e) => setHealthError(String(e)))
  }, [])

  return (
    <div className="app">
      <aside className="sidebar">
        <h1>AI Trading Analyst</h1>
        <div className="sub">XAU/USD · Phase 1 foundation</div>
        {NAV.map((item) => (
          <button
            key={item}
            className={`nav-btn ${active === item ? 'active' : ''}`}
            onClick={() => setActive(item)}
          >
            {item}
          </button>
        ))}
      </aside>

      <main className="main">
        <h2>{active}</h2>

        <div className="card">
          <strong>Backend status</strong>
          <div style={{ marginTop: 8 }}>
            {health ? (
              <span>
                <span className="badge badge-buy">ONLINE</span>{' '}
                <code>{health.app} v{health.version}</code>
              </span>
            ) : healthError ? (
              <span className="muted">
                Backend not reachable ({healthError}). Start it with{' '}
                <code>cd backend &amp;&amp; python run.py</code>
              </span>
            ) : (
              <span className="muted">Checking backend…</span>
            )}
          </div>
        </div>

        <div className="grid">
          <div className="card">
            <strong>Instrument</strong>
            <div className="muted" style={{ marginTop: 6 }}>
              XAU/USD (Gold) — first supported instrument.
            </div>
          </div>
          <div className="card">
            <strong>Signal</strong>
            <div style={{ marginTop: 6 }}>
              <span className="badge badge-wait">WAIT</span>
            </div>
            <div className="muted" style={{ marginTop: 6 }}>
              Signal engine arrives in Phase 4. No fabricated prices are shown.
            </div>
          </div>
          <div className="card">
            <strong>Market data</strong>
            <div className="muted" style={{ marginTop: 6 }}>
              Free data integration arrives in Phase 2. CSV upload will be supported.
            </div>
          </div>
        </div>

        <div className="card">
          <strong>Development roadmap</strong>
          <p className="muted">
            Phase 1: foundation (this screen) · Phase 2: market data · Phase 3: indicators ·
            Phase 4: signal engine · Phase 5: screenshot analysis · Phase 6: backtesting ·
            Phase 7: complete web app · Phase 8: Android.
          </p>
        </div>
      </main>
    </div>
  )
}
