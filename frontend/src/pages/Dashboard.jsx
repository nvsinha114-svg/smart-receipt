
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, FileText, PlusCircle, ReceiptText, Activity } from "lucide-react";
import { getReceipts } from "../services/receiptService";
import { getMedicalReports } from "../services/medicalReportService";

export default function Dashboard(){
  const [receipts, setReceipts] = useState([]);
  const [medReports, setMedReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = () => {
    setLoading(true);
    setError("");
    Promise.all([
      getReceipts().then(r => setReceipts(Array.isArray(r.data) ? r.data : r.data?.content || [])),
      getMedicalReports().then(r => setMedReports(r.data))
    ])
      .catch((err) => {
        setError(err.friendlyMessage || "Failed to load dashboard data. The server may be waking up.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, []);

  const getEffTotal = (r) => {
    if (r.totalAmount != null && Number(r.totalAmount) > 0) return Number(r.totalAmount);
    const items = r.items || [];
    const itemsSum = items.reduce((sum, item) => sum + (Number(item.quantity || 1) * Number(item.price || 0)), 0);
    return itemsSum > 0 ? itemsSum : 0;
  };

  const total = receipts.reduce((s,r)=>{
    const eff = getEffTotal(r);
    return s + (eff != null ? Number(eff) : 0);
  },0);

  const fmt = (val) => val == null ? "Not detected" : `₹${Number(val).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  return (
    <div className="page">
      <header className="topbar">
        <div>
          <span className="eyebrow">Overview</span>
          <h1>Dashboard</h1>
        </div>
        <Link className="primary-btn" to="/upload"><PlusCircle size={18}/> Scan Document</Link>
      </header>

      {error && (
        <div className="alert error" style={{ marginBottom: "20px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span>{error}</span>
          <button className="primary-btn small" onClick={loadData} disabled={loading}>
            Retry
          </button>
        </div>
      )}

      <div className="stats">
        <Stat icon={<ReceiptText/>} label="Total receipts" value={receipts.length}/>
        <Stat icon={<FileText/>} label="Total spending" value={`₹${total.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}/>
        <Stat icon={<Activity/>} label="Medical reports" value={medReports.length}/>
      </div>
      
      <div className="card">
        <div className="section-row">
          <div>
            <h2>Recent receipts</h2>
            <p className="muted">Your latest saved receipts</p>
          </div>
          <Link className="text-link" to="/receipts">View all</Link>
        </div>
        {loading ? (
          <div className="empty">Loading receipts...</div>
        ) : receipts.length === 0 ? (
          <div className="empty">No receipts yet. Scan your first receipt.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Merchant</th>
                  <th>Category</th>
                  <th>Date</th>
                  <th>Total</th>
                </tr>
              </thead>
              <tbody>
                {receipts.slice(0, 6).map((r) => (
                  <tr key={r.id}>
                    <td><Link className="text-link" to={`/receipts/${r.id}`}>{r.merchantName || "Unknown merchant"}</Link></td>
                    <td>{r.category ? <span className="category-badge">{r.category}</span> : <span className="muted">—</span>}</td>
                    <td>{r.receiptDate || "—"}</td>
                    <td>{fmt(getEffTotal(r))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card" style={{ marginTop: "1.5rem" }}>
        <div className="section-row">
          <div>
            <h2>Recent medical reports</h2>
            <p className="muted">Your latest medical report analyses</p>
          </div>
          <Link className="text-link" to="/medical-reports">View all</Link>
        </div>
        {loading ? (
          <div className="empty">Loading medical reports...</div>
        ) : medReports.length === 0 ? (
          <div className="empty">No medical reports yet. Upload one to get started.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Laboratory</th>
                  <th>Date</th>
                  <th>Tests</th>
                  <th>Normal/High/Low</th>
                </tr>
              </thead>
              <tbody>
                {medReports.slice(0, 5).map((m) => (
                  <tr key={m.id}>
                    <td>
                      <Link className="text-link" to={`/medical-reports/${m.id}`}>
                        {m.laboratoryName || "Unknown laboratory"}
                      </Link>
                    </td>
                    <td>{m.reportDate || "—"}</td>
                    <td>{m.summary?.totalParameters || 0}</td>
                    <td>
                      <div style={{ display: "flex", gap: "0.5rem" }}>
                        <span className="category-badge" style={{ background: "rgba(94, 234, 212, 0.12)", color: "#5eead4" }}>
                          N: {m.summary?.normalCount || 0}
                        </span>
                        {m.summary?.highCount > 0 && (
                          <span className="category-badge" style={{ background: "rgba(251, 113, 133, 0.12)", color: "#fb7185" }}>
                            H: {m.summary?.highCount || 0}
                          </span>
                        )}
                        {m.summary?.lowCount > 0 && (
                          <span className="category-badge" style={{ background: "rgba(59, 130, 246, 0.12)", color: "#60a5fa" }}>
                            L: {m.summary?.lowCount || 0}
                          </span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function Stat({ icon, label, value }) {
  return (
    <div className="stat card">
      <div className="feature-icon">{icon}</div>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
    </div>
  );
}