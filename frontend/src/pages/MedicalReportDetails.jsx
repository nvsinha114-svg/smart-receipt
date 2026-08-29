import { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { ChevronLeft, Trash2, ShieldAlert } from "lucide-react";
import { getMedicalReport, deleteMedicalReport } from "../services/medicalReportService";

export default function MedicalReportDetails() {
  const { id } = useParams(), navigate = useNavigate();
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadReport = async () => {
    setLoading(true);
    setError("");
    try {
      const r = await getMedicalReport(id);
      setReport(r.data);
    } catch (err) {
      setError(err.friendlyMessage || "Medical report not found or access denied.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReport();
  }, [id]);

  const handleDelete = async () => {
    if (!window.confirm("Are you sure you want to delete this report? This cannot be undone.")) return;
    try {
      await deleteMedicalReport(id);
      navigate("/medical-reports");
    } catch (err) {
      alert(err.friendlyMessage || "Failed to delete medical report.");
    }
  };

  const getStatusStyle = (status) => {
    switch (status) {
      case "NORMAL":
        return { background: "rgba(94, 234, 212, 0.12)", color: "#5eead4" };
      case "HIGH":
        return { background: "rgba(251, 113, 133, 0.12)", color: "#fb7185", fontWeight: "bold" };
      case "LOW":
        return { background: "rgba(59, 130, 246, 0.12)", color: "#60a5fa", fontWeight: "bold" };
      case "CRITICAL":
      case "ABNORMAL":
        return { background: "rgba(245, 158, 11, 0.15)", color: "#fbbf24", fontWeight: "bold" };
      default:
        return { background: "rgba(255, 255, 255, 0.08)", color: "var(--muted)" };
    }
  };

  if (loading) return <div className="page"><div className="card empty">Loading analysis details...</div></div>;
  if (error) {
    return (
      <div className="page">
        <div className="card empty">
          <p style={{ color: "#fb7185", marginBottom: "16px" }}>{error}</p>
          <div style={{ display: "flex", gap: "12px", justifyContent: "center" }}>
            <button className="primary-btn small" onClick={loadReport}>
              Retry
            </button>
            <Link className="secondary-btn small" to="/medical-reports">
              Back to Reports
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <Link to="/medical-reports" className="back-link">
        <ChevronLeft size={16} /> Back to Reports
      </Link>

      <header className="topbar" style={{ marginTop: "12px", marginBottom: "20px" }}>
        <div>
          <span className="eyebrow">Report Details</span>
          <h1>{report.laboratoryName || "Unknown Laboratory"}</h1>
          <p className="muted">Uploaded file: {report.fileName || "report.pdf"}</p>
        </div>
        <button className="danger-btn" onClick={handleDelete}>
          <Trash2 size={16} /> Delete Report
        </button>
      </header>

      {/* Metadata Card */}
      <div className="card" style={{ marginBottom: "1.5rem" }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "1.5rem" }}>
          <div>
            <span className="eyebrow" style={{ fontSize: "10px" }}>Patient Name</span>
            <div style={{ fontSize: "16px", fontWeight: "700", marginTop: "4px" }}>
              {report.patientName || "N/A"}
            </div>
          </div>
          <div>
            <span className="eyebrow" style={{ fontSize: "10px" }}>Report Date</span>
            <div style={{ fontSize: "16px", fontWeight: "700", marginTop: "4px" }}>
              {report.reportDate || "N/A"}
            </div>
          </div>
          <div>
            <span className="eyebrow" style={{ fontSize: "10px" }}>Total Tests</span>
            <div style={{ fontSize: "16px", fontWeight: "700", marginTop: "4px" }}>
              {report.summary?.totalParameters || 0}
            </div>
          </div>
        </div>
      </div>

      {/* Summary Counts Bar */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: "10px", marginBottom: "1.5rem" }}>
        <div className="card" style={{ padding: "16px", textAlign: "center" }}>
          <div className="muted" style={{ fontSize: "12px" }}>Normal</div>
          <strong style={{ fontSize: "20px", color: "#5eead4" }}>{report.summary?.normalCount || 0}</strong>
        </div>
        <div className="card" style={{ padding: "16px", textAlign: "center" }}>
          <div className="muted" style={{ fontSize: "12px" }}>High</div>
          <strong style={{ fontSize: "20px", color: "#fb7185" }}>{report.summary?.highCount || 0}</strong>
        </div>
        <div className="card" style={{ padding: "16px", textAlign: "center" }}>
          <div className="muted" style={{ fontSize: "12px" }}>Low</div>
          <strong style={{ fontSize: "20px", color: "#60a5fa" }}>{report.summary?.lowCount || 0}</strong>
        </div>
        <div className="card" style={{ padding: "16px", textAlign: "center" }}>
          <div className="muted" style={{ fontSize: "12px" }}>Abnormal</div>
          <strong style={{ fontSize: "20px", color: "#fbbf24" }}>{report.summary?.abnormalCount || 0}</strong>
        </div>
        <div className="card" style={{ padding: "16px", textAlign: "center" }}>
          <div className="muted" style={{ fontSize: "12px" }}>No Ref Range</div>
          <strong style={{ fontSize: "20px", color: "var(--muted)" }}>{report.summary?.referenceUnavailableCount || 0}</strong>
        </div>
      </div>

      {/* Disclaimer Box */}
      <div className="alert error" style={{ background: "rgba(251, 113, 133, 0.06)", border: "1px solid rgba(251, 113, 133, 0.2)", marginBottom: "1.5rem", padding: "14px" }}>
        <ShieldAlert size={20} style={{ color: "#fb7185", flexShrink: 0 }} />
        <span style={{ fontSize: "12px", color: "#fca5a5", lineHeight: "1.4" }}>
          <strong>Medical Disclaimer:</strong> {report.disclaimer}
        </span>
      </div>

      {/* Parameters Table */}
      <div className="card" style={{ marginBottom: "1.5rem" }}>
        <h3 style={{ margin: "0 0 1rem 0" }}>Observed Parameters</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Test Parameter</th>
                <th>Result Value</th>
                <th>Reference Range</th>
                <th>Category</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {report.parameters?.map((param, index) => (
                <tr key={index}>
                  <td>
                    <strong>{param.testName}</strong>
                    {param.labFlag && <span className="item-category" style={{ background: "rgba(251, 113, 133, 0.12)", color: "#fb7185" }}>Flag: {param.labFlag}</span>}
                  </td>
                  <td>
                    <span style={{ fontWeight: "700" }}>{param.value}</span> {param.unit || ""}
                  </td>
                  <td>{param.referenceRange || <span className="muted">N/A</span>}</td>
                  <td>
                    <span className="category-badge" style={{ background: "rgba(255,255,255,0.06)", color: "var(--muted)", textTransform: "none" }}>
                      {param.category || "Other"}
                    </span>
                  </td>
                  <td>
                    <span className="category-badge" style={getStatusStyle(param.status)}>
                      {param.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Overall Notes */}
      {report.overallNotes && report.overallNotes.length > 0 && (
        <div className="card">
          <h3 style={{ margin: "0 0 1rem 0" }}>Analysis Summary & General Notes</h3>
          <ul style={{ paddingLeft: "1.2rem", margin: 0, color: "var(--muted)", lineHeight: "1.6" }}>
            {report.overallNotes.map((note, idx) => (
              <li key={idx} style={{ marginBottom: "8px" }}>{note}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
