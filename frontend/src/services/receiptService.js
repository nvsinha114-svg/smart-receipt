import api from "./api";

export const getReceipts = () => api.get("/api/receipts");
export const getReceipt = (id) => api.get(`/api/receipts/${id}`);
export const createReceipt = (payload) => api.post("/api/receipts", payload);
export const updateReceipt = (id, payload) => api.put(`/api/receipts/${id}`, payload);
export const deleteReceipt = (id) => api.delete(`/api/receipts/${id}`);

export const uploadReceipt = (file) => {
  const form = new FormData();
  form.append("file", file);
  return api.post("/api/receipts/upload", form, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 180000 // 180s to allow for Render cold starts + Tesseract OCR + AI processing
  });
};

export const downloadReceiptPdf = (id) =>
  api.get(`/api/receipts/${id}/pdf`, { responseType: "blob", timeout: 120000 });