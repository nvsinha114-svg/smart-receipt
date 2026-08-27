
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, FileText, PlusCircle, ReceiptText } from "lucide-react";
import { getReceipts } from "../services/receiptService";

export default function Dashboard(){
  const [receipts,setReceipts]=useState([]),[loading,setLoading]=useState(true);
  useEffect(()=>{getReceipts().then(r=>setReceipts(Array.isArray(r.data)?r.data:r.data?.content||[])).catch(()=>{}).finally(()=>setLoading(false))},[]);

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

  return <div className="page"><header className="topbar"><div><span className="eyebrow">Overview</span><h1>Dashboard</h1></div><Link className="primary-btn" to="/upload"><PlusCircle size={18}/> Scan receipt</Link></header>
    <div className="stats"><Stat icon={<ReceiptText/>} label="Total receipts" value={receipts.length}/><Stat icon={<FileText/>} label="Total spending" value={`₹${total.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}/><Stat icon={<ArrowUpRight/>} label="Recent activity" value={receipts.slice(0,5).length}/></div>
    <div className="card"><div className="section-row"><div><h2>Recent receipts</h2><p className="muted">Your latest saved receipts</p></div><Link className="text-link" to="/receipts">View all</Link></div>
      {loading?<div className="empty">Loading receipts...</div>:receipts.length===0?<div className="empty">No receipts yet. Scan your first receipt.</div>:<div className="table-wrap"><table><thead><tr><th>Merchant</th><th>Category</th><th>Date</th><th>Total</th></tr></thead><tbody>{receipts.slice(0,6).map(r=><tr key={r.id}><td><Link className="text-link" to={`/receipts/${r.id}`}>{r.merchantName||"Unknown merchant"}</Link></td><td>{r.category ? <span className="category-badge">{r.category}</span> : <span className="muted">—</span>}</td><td>{r.receiptDate||"—"}</td><td>{fmt(getEffTotal(r))}</td></tr>)}</tbody></table></div>}
    </div>
  </div>
}
function Stat({icon,label,value}){return <div className="stat card"><div className="feature-icon">{icon}</div><div><span>{label}</span><strong>{value}</strong></div></div>}