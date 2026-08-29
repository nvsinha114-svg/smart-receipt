import api from "./api";

export const getMedicalReports = () => api.get("/api/medical-reports");
export const getMedicalReport = (id) => api.get(`/api/medical-reports/${id}`);
export const deleteMedicalReport = (id) => api.delete(`/api/medical-reports/${id}`);

export const analyzeMedicalReport = (file) => {
  const form = new FormData();
  form.append("file", file);
  return api.post("/api/medical-reports/analyze", form, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 180000 // 180s to allow for Render cold starts + Tesseract OCR + AI processing
  });
};
