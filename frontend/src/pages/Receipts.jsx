import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Download, Eye, Trash2 } from "lucide-react";
import { deleteReceipt, downloadReceiptPdf, getReceipts } from "../services/receiptService";

export default function Receipts(){
  const [receipts,setReceipts]=useState([]),[loading,setLoading]=useState(true);
  const load=()=>getReceipts().then(r=>setReceipts(Array.isArray(r.data)?r.data:r.data?.content||[])).finally(()=>setLoading(false));
  useEffect(()=>{load().catch(()=>setLoading(false))},[]);
  const pdf=async(id)=>{const r=await downloadReceiptPdf(id);const url=URL.createObjectURL(r.data);const a=document.createElement("a");a.href=url;a.download=`receipt-${id}.pdf`;a.click();URL.revokeObjectURL(url)};
  const remove=async(id)=>{if(!confirm("Delete this receipt?"))return;await deleteReceipt(id);load()};
  
  const getEffTotal = (r) => {
    const items = r.items || [];
    const itemsSum = items.reduce((sum, item) => sum + (Number(item.quantity || 1) * Number(item.price || 0)), 0);
    return (items.length > 0 && itemsSum > 0) ? itemsSum : r.totalAmount;
  };

  const fmt = (val) => val == null ? "Not detected" : `₹${Number(val).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  return <div className="page"><header className="topbar"><div><span className="eyebrow">Your data</span><h1>Receipts</h1></div><Link className="primary-btn" to="/upload">+ Scan receipt</Link></header><div className="card">{loading?<div className="empty">Loading...</div>:receipts.length===0?<div className="empty">No receipts found.</div>:<div className="table-wrap"><table><thead><tr><th>Merchant</th><th>Category</th><th>Date</th><th>Total</th><th>Actions</th></tr></thead><tbody>{receipts.map(r=><tr key={r.id}><td><b>{r.merchantName||"Unknown"}</b></td><td>{r.category ? <span className="category-badge">{r.category}</span> : <span className="muted">—</span>}</td><td>{r.receiptDate||"—"}</td><td>{fmt(getEffTotal(r))}</td><td><div className="actions"><Link className="icon-btn" title="View" to={`/receipts/${r.id}`}><Eye size={17}/></Link><button className="icon-btn" title="PDF" onClick={()=>pdf(r.id)}><Download size={17}/></button><button className="icon-btn danger" title="Delete" onClick={()=>remove(r.id)}><Trash2 size={17}/></button></div></td></tr>)}</tbody></table></div>}</div></div>
}