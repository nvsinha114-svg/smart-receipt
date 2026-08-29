import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { UploadCloud, FileImage, CheckCircle2, Loader2, AlertCircle, RefreshCw } from "lucide-react";
import { uploadReceipt } from "../services/receiptService";
import { analyzeMedicalReport } from "../services/medicalReportService";

export default function UploadReceipt(){
  const input = useRef();
  const isSubmittingRef = useRef(false);
  const navigate = useNavigate();

  const [file, setFile] = useState(null);
  const [docType, setDocType] = useState("RECEIPT"); // "RECEIPT" or "MEDICAL_REPORT"
  const [busy, setBusy] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);

  // Timer for staged loading experience & cold start tracking
  useEffect(() => {
    let interval = null;
    if (busy) {
      setElapsedSeconds(0);
      interval = setInterval(() => {
        setElapsedSeconds((s) => s + 1);
      }, 1000);
    } else {
      setElapsedSeconds(0);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [busy]);

  const choose = (f) => {
    if (busy) return;
    setError("");
    setResult(null);
    if (!f) return;
    const allowed = ["image/jpeg", "image/png", "application/pdf"];
    if (!allowed.includes(f.type)) return setError("Only JPG, JPEG, PNG and PDF files are supported.");
    if (f.size > 10 * 1024 * 1024) return setError("Maximum file size is 10 MB.");
    setFile(f);
  };

  const getStageMessage = (seconds, isReceipt) => {
    if (seconds < 4) return isReceipt ? "Uploading receipt..." : "Uploading document...";
    if (seconds < 12) return "Processing image...";
    if (seconds < 25) return "Running OCR...";
    if (seconds < 50) return isReceipt ? "Extracting receipt details..." : "Extracting document details...";
    if (seconds < 85) return "Analyzing with AI...";
    return isReceipt ? "Finalizing receipt..." : "Finalizing analysis...";
  };

  const submit = async () => {
    if (isSubmittingRef.current || busy) return;
    if (!file) return setError("Choose a file first.");

    isSubmittingRef.current = true;
    setBusy(true);
    setError("");

    try {
      if (docType === "RECEIPT") {
        const r = await uploadReceipt(file);
        setResult({ type: "RECEIPT", data: r.data });
      } else {
        const r = await analyzeMedicalReport(file);
        setResult({ type: "MEDICAL_REPORT", data: r.data.analysis });
      }
    } catch (e) {
      setError(
        e.friendlyMessage ||
        e.response?.data?.message ||
        "Processing is taking longer than usual. The server may be waking up or the document is still being analyzed. Please wait or try again."
      );
    } finally {
      isSubmittingRef.current = false;
      setBusy(false);
    }
  };

  const isReceipt = docType === "RECEIPT";

  return (
    <div className="page">
      <header className="topbar">
        <div>
          <span className="eyebrow">OCR scanner</span>
          <h1>Upload Document</h1>
          <p className="muted">Upload a receipt or medical report and let Smart Receipt extract its details.</p>
        </div>
      </header>
      
      <div className="upload-layout">
        <div className="card uploader-card">
          <div style={{ marginBottom: "1.5rem", display: "flex", gap: "1.5rem" }}>
            <label style={{ cursor: busy ? "not-allowed" : "pointer", fontWeight: "600", display: "flex", alignItems: "center", gap: "0.5rem", opacity: busy ? 0.6 : 1 }}>
              <input
                type="radio"
                name="docType"
                checked={docType === "RECEIPT"}
                onChange={() => { if (!busy) { setDocType("RECEIPT"); setResult(null); setError(""); } }}
                disabled={busy}
              />
              Financial Receipt
            </label>
            <label style={{ cursor: busy ? "not-allowed" : "pointer", fontWeight: "600", display: "flex", alignItems: "center", gap: "0.5rem", opacity: busy ? 0.6 : 1 }}>
              <input
                type="radio"
                name="docType"
                checked={docType === "MEDICAL_REPORT"}
                onChange={() => { if (!busy) { setDocType("MEDICAL_REPORT"); setResult(null); setError(""); } }}
                disabled={busy}
              />
              Medical Report
            </label>
          </div>

          <div
            className="card uploader"
            style={{ cursor: busy ? "default" : "pointer" }}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              if (!busy) choose(e.dataTransfer.files[0]);
            }}
            onClick={() => {
              if (!file && !busy) input.current?.click();
            }}
          >
            <input
              ref={input}
              hidden
              type="file"
              accept=".jpg,.jpeg,.png,.pdf"
              disabled={busy}
              onChange={(e) => choose(e.target.files[0])}
            />
            {!file ? (
              <>
                <div className="upload-icon"><UploadCloud/></div>
                <h2>Drop your document here</h2>
                <p>or click to browse</p>
                <small>JPG, JPEG, PNG or PDF · max 10 MB</small>
              </>
            ) : (
              <>
                <div className="upload-icon"><FileImage/></div>
                <h2>{file.name}</h2>
                <p>{(file.size / 1024 / 1024).toFixed(2)} MB</p>
                {!busy && (
                  <button
                    className="secondary-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      setFile(null);
                      setResult(null);
                      setError("");
                    }}
                  >
                    Choose another
                  </button>
                )}
              </>
            )}

            {/* Indeterminate Progress Box with Staged Messages */}
            {busy && (
              <div className="progress-box" onClick={(e) => e.stopPropagation()}>
                <div className="progress-header">
                  <div className="progress-stage-title">
                    <span className="pulse-dot" />
                    <span>{getStageMessage(elapsedSeconds, isReceipt)}</span>
                  </div>
                  <span className="progress-time-counter">{elapsedSeconds}s</span>
                </div>
                <div className="progress-track">
                  <div className="progress-bar-indeterminate" />
                </div>
                {elapsedSeconds >= 25 && (
                  <div className="progress-hint">
                    <span>⏳</span>
                    <span>The server is processing your document. On initial request, free-tier hosting may take 30-60s to warm up.</span>
                  </div>
                )}
              </div>
            )}

            {/* Error Message with Safe Manual Retry */}
            {error && (
              <div className="alert error alert-with-actions" onClick={(e) => e.stopPropagation()} style={{ marginTop: "16px" }}>
                <div style={{ display: "flex", alignItems: "flex-start", gap: "8px" }}>
                  <AlertCircle size={18} style={{ flexShrink: 0, marginTop: "2px" }} />
                  <div>{error}</div>
                </div>
                {file && (
                  <div className="alert-actions">
                    <button
                      className="primary-btn small"
                      onClick={(e) => {
                        e.stopPropagation();
                        submit();
                      }}
                      disabled={busy}
                    >
                      <RefreshCw size={14} /> Try Again
                    </button>
                  </div>
                )}
              </div>
            )}

            {/* Action Submit Button */}
            {file && (
              <button
                className="primary-btn full upload-submit"
                onClick={(e) => {
                  e.stopPropagation();
                  submit();
                }}
                disabled={busy}
                style={{ opacity: busy ? 0.7 : 1 }}
              >
                {busy ? (
                  <>
                    <Loader2 size={16} className="spin" />
                    Processing...
                  </>
                ) : (
                  "Upload & extract"
                )}
              </button>
            )}
          </div>
        </div>
        
        <div className="card tips">
          <h3>What happens next?</h3>
          <div className="tip"><span>1</span><p>Your file is processed securely.</p></div>
          <div className="tip"><span>2</span><p>Tesseract OCR extracts readable document text.</p></div>
          <div className="tip"><span>3</span><p>AI identifies the document contents and extracts parameters.</p></div>
          
          {result && result.type === "RECEIPT" && (
            <div className="alert success" style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <CheckCircle2 size={17}/>
                <strong>Document Type: Receipt</strong>
              </div>
              <p style={{ margin: "4px 0" }}>
                Receipt processed successfully. 
                {result.data.totalAmount !== null && result.data.totalAmount !== undefined ? ` Extracted total: ₹${Number(result.data.totalAmount).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}${result.data.category ? ` (Category: ${result.data.category})` : ""}.` : " Total could not be detected."}
              </p>
              {result.data.id && <Link to={`/receipts/${result.data.id}`} className="primary-btn text-center" style={{ width: "fit-content", textDecoration: "none" }}>View receipt</Link>}
            </div>
          )}

          {result && result.type === "MEDICAL_REPORT" && (
            <div className="alert success" style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <CheckCircle2 size={17}/>
                <strong>Document Type: Medical Report</strong>
              </div>
              <p style={{ margin: "4px 0" }}>
                Medical report analyzed successfully.
                Total Parameters: {result.data.summary?.totalParameters || 0} (Normal: {result.data.summary?.normalCount || 0}, High: {result.data.summary?.highCount || 0}, Low: {result.data.summary?.lowCount || 0})
              </p>
              {result.data.id && <Link to={`/medical-reports/${result.data.id}`} className="primary-btn text-center" style={{ width: "fit-content", textDecoration: "none" }}>View Analysis</Link>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}