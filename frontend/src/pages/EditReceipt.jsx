import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getReceipt, updateReceipt } from "../services/receiptService";
import ReceiptForm from "../components/ReceiptForm";

export default function EditReceipt(){
  const { id } = useParams(), navigate = useNavigate();
  const [receipt, setReceipt] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    getReceipt(id)
      .then((r) => setReceipt(r.data))
      .catch((e) => setError(e.friendlyMessage || e.response?.data?.message || "Could not load receipt"));
  }, [id]);

  const submit = async (data) => {
    setLoading(true);
    setError("");
    try {
      await updateReceipt(id, data);
      navigate(`/receipts/${id}`);
    } catch (e) {
      setError(e.friendlyMessage || e.response?.data?.message || "Update failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <header className="topbar">
        <div>
          <span className="eyebrow">Edit</span>
          <h1>Edit receipt</h1>
        </div>
      </header>
      {error && <div className="alert error">{error}</div>}
      {receipt && <ReceiptForm initial={receipt} onSubmit={submit} loading={loading} />}
    </div>
  );
}