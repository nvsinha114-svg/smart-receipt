import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { UploadCloud, FileImage, CheckCircle2 } from "lucide-react";
import { uploadReceipt } from "../services/receiptService";

export default function UploadReceipt(){
  const input=useRef(), navigate=useNavigate(), [file,setFile]=useState(null), [busy,setBusy]=useState(false), [error,setError]=useState(""), [result,setResult]=useState(null);
  const choose=(f)=>{setError("");if(!f)return;const allowed=["image/jpeg","image/png","application/pdf"];if(!allowed.includes(f.type))return setError("Only JPG, JPEG, PNG and PDF files are supported.");if(f.size>10*1024*1024)return setError("Maximum file size is 10 MB.");setFile(f)};
  const submit=async()=>{if(!file)return setError("Choose a receipt file first.");setBusy(true);setError("");try{const r=await uploadReceipt(file);setResult(r.data)}catch(e){setError(e.response?.data?.message||"OCR upload failed.")}finally{setBusy(false)}};
  return <div className="page"><header className="topbar"><div><span className="eyebrow">OCR scanner</span><h1>Scan receipt</h1><p className="muted">Upload a receipt and let Smart Receipt extract its details.</p></div></header>
    <div className="upload-layout"><div className="card uploader" onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();choose(e.dataTransfer.files[0])}} onClick={()=>!file&&input.current?.click()}>
      <input ref={input} hidden type="file" accept=".jpg,.jpeg,.png,.pdf" onChange={e=>choose(e.target.files[0])}/>
      {!file?<><div className="upload-icon"><UploadCloud/></div><h2>Drop your receipt here</h2><p>or click to browse</p><small>JPG, JPEG, PNG or PDF · max 10 MB</small></>:<><div className="upload-icon"><FileImage/></div><h2>{file.name}</h2><p>{(file.size/1024/1024).toFixed(2)} MB</p><button className="secondary-btn" onClick={(e)=>{e.stopPropagation();setFile(null)}}>Choose another</button></>}
      {error&&<div className="alert error">{error}</div>}
      {file&&<button className="primary-btn full upload-submit" onClick={(e)=>{e.stopPropagation();submit()}} disabled={busy}>{busy?"Processing OCR...":"Upload & extract"}</button>}
    </div>
    <div className="card tips"><h3>What happens next?</h3><div className="tip"><span>1</span><p>Your file is processed temporarily.</p></div><div className="tip"><span>2</span><p>Tesseract OCR extracts readable receipt text.</p></div><div className="tip"><span>3</span><p>Detected receipt data is saved to MongoDB.</p></div>{result&&<div className="alert success"><CheckCircle2 size={17}/> Receipt processed successfully. {result.totalAmount !== null && result.totalAmount !== undefined ? `Extracted total: ₹${Number(result.totalAmount).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}.` : "Total could not be detected."} {result.id&&<Link to={`/receipts/${result.id}`}>View receipt</Link>}</div>}</div></div>
  </div>
}