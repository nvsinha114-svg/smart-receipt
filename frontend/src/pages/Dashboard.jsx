import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, FileText, PlusCircle, ReceiptText } from "lucide-react";
import { getReceipts } from "../services/receiptService";

export default function Dashboard(){
  const [receipts,setReceipts]=useState([]),[loading,setLoading]=useState(true);
  useEffect(()=>{getReceipts().then(r=>setReceipts(Array.isArray(r.data)?r.data:r.data?.content||[])).catch(()=>{}).finally(()=>setLoading(false))},[]);
  const total=receipts.reduce((s,r)=>s+Number(r.totalAmount||0),0);
  return <div className="page"><header className="topbar"><div><span className="eyebrow">Overview</span><h1>Dashboard</h1></div><Link className="primary-btn" to="/upload"><PlusCircle size={18}/> Scan receipt</Link></header>
    <div className="stats"><Stat icon={<ReceiptText/>} label="Total receipts" value={receipts.length}/><Stat icon={<FileText/>} label="Total spending" value={`₹${total.toFixed(2)}`}/><Stat icon={<ArrowUpRight/>} label="Recent activity" value={receipts.slice(0,5).length}/></div>
    <div className="card"><div className="section-row"><div><h2>Recent receipts</h2><p className="muted">Your latest saved receipts</p></div><Link className="text-link" to="/receipts">View all</Link></div>
      {loading?<div className="empty">Loading receipts...</div>:receipts.length===0?<div className="empty">No receipts yet. Scan your first receipt.</div>:<div className="table-wrap"><table><thead><tr><th>Merchant</th><th>Date</th><th>Total</th></tr></thead><tbody>{receipts.slice(0,6).map(r=><tr key={r.id}><td><Link className="text-link" to={`/receipts/${r.id}`}>{r.merchantName||"Unknown merchant"}</Link></td><td>{r.receiptDate||"—"}</td><td>₹{Number(r.totalAmount||0).toFixed(2)}</td></tr>)}</tbody></table></div>}
    </div>
  </div>
}
function Stat({icon,label,value}){return <div className="stat card"><div className="feature-icon">{icon}</div><div><span>{label}</span><strong>{value}</strong></div></div>}