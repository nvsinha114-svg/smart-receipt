import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { UploadCloud, FileImage, CheckCircle2 } from "lucide-react";
import { uploadReceipt } from "../services/receiptService";
import { analyzeMedicalReport } from "../services/medicalReportService";

export default function UploadReceipt(){
  const input=useRef(), navigate=useNavigate();
  const [file,setFile]=useState(null);
  const [docType,setDocType]=useState("RECEIPT"); // "RECEIPT" or "MEDICAL_REPORT"
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const [result,setResult]=useState(null);

  const choose=(f)=>{
    setError("");
    setResult(null);
    if(!f)return;
    const allowed=["image/jpeg","image/png","application/pdf"];
    if(!allowed.includes(f.type))return setError("Only JPG, JPEG, PNG and PDF files are supported.");
    if(f.size>10*1024*1024)return setError("Maximum file size is 10 MB.");
    setFile(f)
  };

  const submit=async()=>{
    if(!file)return setError("Choose a file first.");
    setBusy(true);
    setError("");
    try{
      if (docType === "RECEIPT") {
        const r = await uploadReceipt(file);
        setResult({ type: "RECEIPT", data: r.data });
      } else {
        const r = await analyzeMedicalReport(file);
        setResult({ type: "MEDICAL_REPORT", data: r.data.analysis });
      }
    }catch(e){
      setError(e.response?.data?.message||"OCR upload failed.");
    }finally{
      setBusy(false);
    }
  };

  return <div className="page">
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
          <label style={{ cursor: "pointer", fontWeight: "600", display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <input type="radio" name="docType" checked={docType === "RECEIPT"} onChange={()=> { setDocType("RECEIPT"); setResult(null); }} disabled={busy}/>
            Financial Receipt
          </label>
          <label style={{ cursor: "pointer", fontWeight: "600", display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <input type="radio" name="docType" checked={docType === "MEDICAL_REPORT"} onChange={()=> { setDocType("MEDICAL_REPORT"); setResult(null); }} disabled={busy}/>
            Medical Report
          </label>
        </div>

        <div className="card uploader" onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();choose(e.dataTransfer.files[0])}} onClick={()=>!file&&input.current?.click()}>
          <input ref={input} hidden type="file" accept=".jpg,.jpeg,.png,.pdf" onChange={e=>choose(e.target.files[0])}/>
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
              <p>{(file.size/1024/1024).toFixed(2)} MB</p>
              <button className="secondary-btn" onClick={(e)=>{e.stopPropagation();setFile(null);setResult(null);}}>Choose another</button>
            </>
          )}
          {error&&<div className="alert error">{error}</div>}
          {file&&<button className="primary-btn full upload-submit" onClick={(e)=>{e.stopPropagation();submit()}} disabled={busy}>{busy?"Processing OCR...":"Upload & extract"}</button>}
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
}