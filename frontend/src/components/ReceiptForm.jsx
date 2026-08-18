import { useState } from "react";

const CATEGORIES = ["", "Food", "Transportation", "Shopping", "Education", "Healthcare", "Utilities", "Other"];

export default function ReceiptForm({ initial = {}, onSubmit, loading = false }) {
  const [form, setForm] = useState({
    merchantName: initial.merchantName || "",
    receiptDate: initial.receiptDate || "",
    totalAmount: initial.totalAmount ?? "",
    category: initial.category || "",
    items: initial.items?.length ? initial.items.map(it => ({
      name: it.name || "",
      category: it.category || "",
      quantity: it.quantity || 1,
      price: it.price || ""
    })) : [{ name: "", category: "", quantity: 1, price: "" }]
  });

  const calcTotal = (items) => items.reduce((sum, item) => sum + (Number(item.quantity || 1) * Number(item.price || 0)), 0);

  const set = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  
  const setItem = (i, key, value) => {
    const items = [...form.items];
    items[i] = { ...items[i], [key]: value };
    const itemsSum = calcTotal(items);
    setForm((f) => ({
      ...f,
      items,
      totalAmount: itemsSum > 0 ? itemsSum : f.totalAmount
    }));
  };

  const addItem = () => {
    const items = [...form.items, { name: "", category: "", quantity: 1, price: "" }];
    set("items", items);
  };
  
  const removeItem = (i) => {
    const items = form.items.filter((_, idx) => idx !== i);
    const itemsSum = calcTotal(items);
    setForm((f) => ({
      ...f,
      items,
      totalAmount: itemsSum > 0 ? itemsSum : f.totalAmount
    }));
  };

  const submit = (e) => {
    e.preventDefault();
    const itemsSum = calcTotal(form.items);
    const finalTotal = itemsSum > 0 ? itemsSum : Number(form.totalAmount);
    onSubmit({
      ...form,
      totalAmount: finalTotal,
      items: form.items.map((x) => ({ ...x, quantity: Number(x.quantity), price: Number(x.price) }))
    });
  };

  return (
    <form className="card form-card" onSubmit={submit}>
      <div className="form-grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))" }}>
        <label>Merchant name<input required value={form.merchantName} onChange={e=>set("merchantName",e.target.value)} /></label>
        <label>Receipt date<input type="date" value={form.receiptDate} onChange={e=>set("receiptDate",e.target.value)} /></label>
        <label>Total amount (₹)<input required type="number" min="0" step="0.01" value={form.totalAmount} onChange={e=>set("totalAmount",e.target.value)} /></label>
        <label>Category
          <select value={form.category} onChange={e=>set("category",e.target.value)} style={{width:"100%",padding:"12px 13px",borderRadius:"10px",border:"1px solid var(--border)",background:"#0b1220",color:"#fff",height:"45px"}}>
            {CATEGORIES.map(c => <option key={c} value={c}>{c || "No category"}</option>)}
          </select>
        </label>
      </div>

      <div className="section-row"><h3>Items</h3><button type="button" className="secondary-btn" onClick={addItem}>+ Add item</button></div>
      <div className="items-editor">
        {form.items.map((item, i) => (
          <div className="item-row" key={i}>
            <input placeholder="Item name" value={item.name} onChange={e=>setItem(i,"name",e.target.value)} required />
            <select value={item.category || ""} onChange={e=>setItem(i,"category",e.target.value)} style={{width:"100%",padding:"11px 12px",borderRadius:"10px",border:"1px solid var(--border)",background:"#0b1220",color:"#fff",height:"42px"}}>
              {CATEGORIES.map(c => <option key={c} value={c}>{c || "No category"}</option>)}
            </select>
            <input type="number" min="1" placeholder="Qty" value={item.quantity} onChange={e=>setItem(i,"quantity",e.target.value)} required />
            <input type="number" min="0" step="0.01" placeholder="Unit Price (₹)" value={item.price} onChange={e=>setItem(i,"price",e.target.value)} required />
            {form.items.length > 1 && <button type="button" className="icon-btn danger" onClick={()=>removeItem(i)}>×</button>}
          </div>
        ))}
      </div>
      <button className="primary-btn" disabled={loading}>{loading ? "Saving..." : "Save receipt"}</button>
    </form>
  );
}