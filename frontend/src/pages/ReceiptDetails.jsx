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

  const items = r.items || [];
  const itemsSum = items.reduce((sum, item) => sum + (Number(item.quantity || 1) * Number(item.price || 0)), 0);
  const effectiveTotal = (r.totalAmount != null && Number(r.totalAmount) > 0) ? Number(r.totalAmount) : (itemsSum > 0 ? itemsSum : null);

  const fmt = (val) => val == null ? "Not detected" : `₹${Number(val).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  const taxes = r.taxes || [];

  return <div className="page"><header className="topbar"><div><Link className="back-link" to="/receipts"><ArrowLeft size={16}/> Back</Link><h1>{r.merchantName||"Receipt"}{r.category && <span className="category-badge" style={{marginLeft: "12px"}}>{r.category}</span>}</h1><p className="muted">{r.receiptDate||"No date"} · ID {r.id}</p></div><div className="actions"><button className="secondary-btn" onClick={pdf}><Download size={17}/> PDF</button><Link className="secondary-btn" to={`/receipts/${id}/edit`}><Pencil size={17}/> Edit</Link><button className="danger-btn" onClick={remove}><Trash2 size={17}/> Delete</button></div></header>
    <div className="card receipt-detail">
      <div className="receipt-total"><span>Total amount</span><strong>{fmt(effectiveTotal)}</strong></div>
      <h3>Items</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Item</th><th>Category</th><th>Qty</th><th>Unit Price</th><th>Subtotal</th></tr>
          </thead>
          <tbody>
            {items.map((x,i)=>{
              const qty = Number(x.quantity || 1);
              const price = Number(x.price || 0);
              const subtotal = qty * price;
              return <tr key={i}><td>{x.name}</td><td>{x.category ? <span className="category-badge">{x.category}</span> : <span className="muted">—</span>}</td><td>{qty}</td><td>{fmt(price)}</td><td>{fmt(subtotal)}</td></tr>;
            })}
          </tbody>
        </table>
      </div>

      {(r.subtotal != null || r.discount != null || r.shippingAmount != null || taxes.length > 0 || r.totalTax != null) && (
        <div style={{ marginTop: "24px", paddingTop: "16px", borderTop: "1px solid #e2e8f0" }}>
          <h3>Tax & Summary Breakdown</h3>
          <table style={{ width: "100%", maxWidth: "400px", marginLeft: "auto", fontSize: "0.95rem" }}>
            <tbody>
              {r.subtotal != null && <tr><td style={{ padding: "4px 0", color: "#64748b" }}>Subtotal / Taxable Amount</td><td style={{ textAlign: "right", fontWeight: "600" }}>{fmt(r.subtotal)}</td></tr>}
              {taxes.map((t, i) => (
                <tr key={i}><td style={{ padding: "4px 0", color: "#64748b" }}>{t.type} {t.rate != null ? `(${t.rate}%)` : ""}</td><td style={{ textAlign: "right", fontWeight: "600" }}>{fmt(t.amount)}</td></tr>
              ))}
              {r.totalTax != null && taxes.length === 0 && <tr><td style={{ padding: "4px 0", color: "#64748b" }}>Total Tax</td><td style={{ textAlign: "right", fontWeight: "600" }}>{fmt(r.totalTax)}</td></tr>}
              {r.discount != null && Number(r.discount) > 0 && <tr><td style={{ padding: "4px 0", color: "#64748b" }}>Discount</td><td style={{ textAlign: "right", color: "#e11d48", fontWeight: "600" }}>-{fmt(r.discount)}</td></tr>}
              {r.shippingAmount != null && Number(r.shippingAmount) > 0 && <tr><td style={{ padding: "4px 0", color: "#64748b" }}>Shipping / Delivery</td><td style={{ textAlign: "right", fontWeight: "600" }}>{fmt(r.shippingAmount)}</td></tr>}
              <tr style={{ borderTop: "1px solid #cbd5e1", fontWeight: "700" }}><td style={{ padding: "8px 0" }}>Grand Total</td><td style={{ textAlign: "right", padding: "8px 0", color: "#ffffff" }}>{fmt(effectiveTotal)}</td></tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  </div>
}