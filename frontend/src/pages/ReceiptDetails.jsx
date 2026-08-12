import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Download, Pencil, Trash2 } from "lucide-react";
import { deleteReceipt, downloadReceiptPdf, getReceipt } from "../services/receiptService";

export default function ReceiptDetails(){
  const {id}=useParams(),navigate=useNavigate(),[r,setR]=useState(null),[error,setError]=useState("");
  useEffect(()=>{getReceipt(id).then(x=>setR(x.data)).catch(e=>setError(e.response?.data?.message||"Receipt not found"))},[id]);
  const pdf=async()=>{const x=await downloadReceiptPdf(id);const url=URL.createObjectURL(x.data);const a=document.createElement("a");a.href=url;a.download=`receipt-${id}.pdf`;a.click();URL.revokeObjectURL(url)};
  const remove=async()=>{if(!confirm("Delete this receipt?"))return;await deleteReceipt(id);navigate("/receipts")};
  if(error)return <div className="page"><div className="card empty">{error}<br/><Link className="text-link" to="/receipts">Back to receipts</Link></div></div>;
  if(!r)return <div className="page"><div className="card empty">Loading...</div></div>;
  return <div className="page"><header className="topbar"><div><Link className="back-link" to="/receipts"><ArrowLeft size={16}/> Back</Link><h1>{r.merchantName||"Receipt"}</h1><p className="muted">{r.receiptDate||"No date"} · ID {r.id}</p></div><div className="actions"><button className="secondary-btn" onClick={pdf}><Download size={17}/> PDF</button><Link className="secondary-btn" to={`/receipts/${id}/edit`}><Pencil size={17}/> Edit</Link><button className="danger-btn" onClick={remove}><Trash2 size={17}/> Delete</button></div></header>
    <div className="card receipt-detail"><div className="receipt-total"><span>Total amount</span><strong>₹{Number(r.totalAmount||0).toFixed(2)}</strong></div><h3>Items</h3><div className="table-wrap"><table><thead><tr><th>Item</th><th>Qty</th><th>Price</th></tr></thead><tbody>{(r.items||[]).map((x,i)=><tr key={i}><td>{x.name}</td><td>{x.quantity}</td><td>₹{Number(x.price||0).toFixed(2)}</td></tr>)}</tbody></table></div></div>
  </div>
}