import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Download, Eye, Trash2, Loader2, RefreshCw, AlertCircle } from "lucide-react";
import { deleteReceipt, downloadReceiptPdf, getReceipts } from "../services/receiptService";

export default function Receipts(){
  const [receipts, setReceipts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [downloadingId, setDownloadingId] = useState(null);
  const [pdfError, setPdfError] = useState("");

  const load = () => {
    setLoading(true);
    setError("");
    getReceipts()
      .then((r) => setReceipts(Array.isArray(r.data) ? r.data : r.data?.content || []))
      .catch((e) => setError(e.friendlyMessage || "Failed to load receipts."))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const pdf = async (id) => {
    if (downloadingId) return;
    setDownloadingId(id);
    setPdfError("");
    try {
      const r = await downloadReceiptPdf(id);
      const url = URL.createObjectURL(r.data);
      const a = document.createElement("a");
      a.href = url;
      a.download = `receipt-${id}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setPdfError(
        e.friendlyMessage ||
        "PDF generation is taking longer than usual. Please try again."
      );
    } finally {
      setDownloadingId(null);
    }
  };

  const remove = async (id) => {
    if (!confirm("Delete this receipt?")) return;
    try {
      await deleteReceipt(id);
      load();
    } catch (e) {
      alert(e.friendlyMessage || "Failed to delete receipt.");
    }
  };
  
  const getEffTotal = (r) => {
    if (r.totalAmount != null && Number(r.totalAmount) > 0) return r.totalAmount;
    const items = r.items || [];
    const itemsSum = items.reduce((sum, item) => sum + (Number(item.quantity || 1) * Number(item.price || 0)), 0);
    return itemsSum > 0 ? itemsSum : null;
  };

  const fmt = (val) => val == null ? "Not detected" : `₹${Number(val).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  return (
    <div className="page">
      <header className="topbar">
        <div>
          <span className="eyebrow">Your data</span>
          <h1>Receipts</h1>
        </div>
        <Link className="primary-btn" to="/upload">+ Scan receipt</Link>
      </header>

      {pdfError && (
        <div className="alert error" style={{ marginBottom: "16px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <AlertCircle size={18} />
            <span>{pdfError}</span>
          </div>
          <button className="ghost-btn" style={{ fontSize: "12px", padding: "4px 8px" }} onClick={() => setPdfError("")}>
            Dismiss
          </button>
        </div>
      )}

      <div className="card">
        {loading ? (
          <div className="empty">
            <Loader2 size={24} className="spin" style={{ margin: "0 auto 12px" }} />
            Loading receipts...
          </div>
        ) : error ? (
          <div className="empty">
            <p style={{ color: "#fb7185", marginBottom: "16px" }}>{error}</p>
            <button className="primary-btn small" onClick={load}>
              <RefreshCw size={14} /> Retry
            </button>
          </div>
        ) : receipts.length === 0 ? (
          <div className="empty">No receipts found.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Merchant</th>
                  <th>Category</th>
                  <th>Date</th>
                  <th>Total</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {receipts.map((r) => (
                  <tr key={r.id}>
                    <td><b>{r.merchantName || "Unknown"}</b></td>
                    <td>{r.category ? <span className="category-badge">{r.category}</span> : <span className="muted">—</span>}</td>
                    <td>{r.receiptDate || "—"}</td>
                    <td>{fmt(getEffTotal(r))}</td>
                    <td>
                      <div className="actions">
                        <Link className="icon-btn" title="View" to={`/receipts/${r.id}`}>
                          <Eye size={17} />
                        </Link>
                        <button
                          className="icon-btn"
                          title="PDF"
                          onClick={() => pdf(r.id)}
                          disabled={downloadingId === r.id}
                        >
                          {downloadingId === r.id ? <Loader2 size={16} className="spin" /> : <Download size={17} />}
                        </button>
                        <button className="icon-btn danger" title="Delete" onClick={() => remove(r.id)}>
                          <Trash2 size={17} />
                        </button>
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