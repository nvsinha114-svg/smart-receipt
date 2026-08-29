import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FileText, Trash2, Eye, PlusCircle } from "lucide-react";
import { getMedicalReports, deleteMedicalReport } from "../services/medicalReportService";

export default function MedicalReports() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchReports = async () => {
    setLoading(true);
    setError("");
    try {
      const r = await getMedicalReports();
      setReports(r.data);
    } catch (err) {
      setError(err.friendlyMessage || "Failed to load medical reports.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReports();
  }, []);

  const handleDelete = async (id) => {
    if (!window.confirm("Are you sure you want to delete this report? This cannot be undone.")) return;
    try {
      await deleteMedicalReport(id);
      setReports(reports.filter((r) => r.id !== id));
    } catch (err) {
      alert(err.friendlyMessage || "Failed to delete medical report.");
    }
  };

  return (
    <div className="page">
      <header className="topbar">
        <div>
          <span className="eyebrow">Records</span>
          <h1>My Medical Reports</h1>
          <p className="muted">View and manage your health analyses dynamically extracted by AI.</p>
        </div>
        <Link to="/upload" className="primary-btn">
          <PlusCircle size={18} /> Upload Document
        </Link>
      </header>

      {loading ? (
        <div className="empty">Loading medical reports...</div>
      ) : error ? (
        <div className="card empty">
          <p style={{ color: "#fb7185", marginBottom: "16px" }}>{error}</p>
          <button className="primary-btn small" onClick={fetchReports}>
            Retry
          </button>
        </div>
      ) : reports.length === 0 ? (
        <div className="card empty">
          <FileText size={48} style={{ marginBottom: "1rem", color: "var(--muted)" }} />
          <h3>No medical reports found</h3>
          <p className="muted">Upload your blood test or lab report to extract dynamic parameter insights.</p>
          <Link to="/upload" className="primary-btn" style={{ marginTop: "1rem" }}>
            Scan a report
          </Link>
        </div>
      ) : (
        <div className="card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Laboratory / File</th>
                <th>Report Date</th>
                <th>Total Tests</th>
                <th>Summary Details</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {reports.map((report) => (
                <tr key={report.id}>
                  <td>
                    <strong>{report.laboratoryName || "Unknown Laboratory"}</strong>
                    <div className="muted" style={{ fontSize: "12px", marginTop: "2px" }}>
                      {report.fileName || "report.pdf"}
                    </div>
                  </td>
                  <td>{report.reportDate ? report.reportDate : <span className="muted">N/A</span>}</td>
                  <td>
                    <span style={{ fontWeight: "700" }}>{report.summary?.totalParameters || 0}</span> parameters
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                      <span className="category-badge" style={{ background: "rgba(94, 234, 212, 0.12)", color: "#5eead4" }}>
                        Normal: {report.summary?.normalCount || 0}
                      </span>
                      {report.summary?.highCount > 0 && (
                        <span className="category-badge" style={{ background: "rgba(251, 113, 133, 0.12)", color: "#fb7185" }}>
                          High: {report.summary?.highCount || 0}
                        </span>
                      )}
                      {report.summary?.lowCount > 0 && (
                        <span className="category-badge" style={{ background: "rgba(59, 130, 246, 0.12)", color: "#60a5fa" }}>
                          Low: {report.summary?.lowCount || 0}
                        </span>
                      )}
                      {report.summary?.abnormalCount > 0 && (
                        <span className="category-badge" style={{ background: "rgba(245, 158, 11, 0.12)", color: "#fbbf24" }}>
                          Abnormal: {report.summary?.abnormalCount || 0}
                        </span>
                      )}
                    </div>
                  </td>
                  <td>
                    <div className="actions">
                      <Link to={`/medical-reports/${report.id}`} className="icon-btn" title="View details">
                        <Eye size={16} />
                      </Link>
                      <button className="icon-btn danger" title="Delete" onClick={() => handleDelete(report.id)}>
                        <Trash2 size={16} />
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
  );
}
