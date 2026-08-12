import { useState } from "react";

export default function ReceiptForm({ initial = {}, onSubmit, loading = false }) {
  const [form, setForm] = useState({
    merchantName: initial.merchantName || "",
    receiptDate: initial.receiptDate || "",
    totalAmount: initial.totalAmount ?? "",
    items: initial.items?.length ? initial.items : [{ name: "", quantity: 1, price: "" }]
  });

  const set = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  const setItem = (i, key, value) => {
    const items = [...form.items];
    items[i] = { ...items[i], [key]: value };
    set("items", items);
  };

  const addItem = () => set("items", [...form.items, { name: "", quantity: 1, price: "" }]);
  const removeItem = (i) => set("items", form.items.filter((_, idx) => idx !== i));

  const submit = (e) => {
    e.preventDefault();
    onSubmit({
      ...form,
      totalAmount: Number(form.totalAmount),
      items: form.items.map((x) => ({ ...x, quantity: Number(x.quantity), price: Number(x.price) }))
    });
  };

  return (
    <form className="card form-card" onSubmit={submit}>
      <div className="form-grid">
        <label>Merchant name<input required value={form.merchantName} onChange={e=>set("merchantName",e.target.value)} /></label>
        <label>Receipt date<input type="date" value={form.receiptDate} onChange={e=>set("receiptDate",e.target.value)} /></label>
        <label>Total amount<input required type="number" min="0" step="0.01" value={form.totalAmount} onChange={e=>set("totalAmount",e.target.value)} /></label>
      </div>

      <div className="section-row"><h3>Items</h3><button type="button" className="secondary-btn" onClick={addItem}>+ Add item</button></div>
      <div className="items-editor">
        {form.items.map((item, i) => (
          <div className="item-row" key={i}>
            <input placeholder="Item name" value={item.name} onChange={e=>setItem(i,"name",e.target.value)} required />
            <input type="number" min="1" placeholder="Qty" value={item.quantity} onChange={e=>setItem(i,"quantity",e.target.value)} required />
            <input type="number" min="0" step="0.01" placeholder="Price" value={item.price} onChange={e=>setItem(i,"price",e.target.value)} required />
            {form.items.length > 1 && <button type="button" className="icon-btn danger" onClick={()=>removeItem(i)}>×</button>}
          </div>
        ))}
      </div>
      <button className="primary-btn" disabled={loading}>{loading ? "Saving..." : "Save receipt"}</button>
    </form>
  );
}