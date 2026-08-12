import api from './api';

export const receiptService = {
  getAllReceipts: async () => {
    const response = await api.get('/api/receipts');
    return response.data;
  },

  getReceiptById: async (id) => {
    const response = await api.get(`/api/receipts/${id}`);
    return response.data;
  },

  createReceipt: async (receiptData) => {
    const response = await api.post('/api/receipts', receiptData);
    return response.data;
  },

  updateReceipt: async (id, receiptData) => {
    const response = await api.put(`/api/receipts/${id}`, receiptData);
    return response.data;
  },

  deleteReceipt: async (id) => {
    const response = await api.delete(`/api/receipts/${id}`);
    return response.data;
  },

  uploadReceipt: async (file, onUploadProgress) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await api.post('/api/receipts/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress,
    });
    return response.data;
  },

  downloadReceiptPdf: async (id, filename = `receipt_${id}.pdf`) => {
    const response = await api.get(`/api/receipts/${id}/pdf`, {
      responseType: 'blob',
    });

    // Create a blob URL and trigger browser file download
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },
};

export default receiptService;
