import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  timeout: 60000
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("smart_receipt_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("smart_receipt_token");
      localStorage.removeItem("smart_receipt_user");
      window.dispatchEvent(new Event("auth-expired"));
    }

    // Attach structured error info for timeouts and network issues
    const isTimeout =
      error.code === "ECONNABORTED" ||
      error.code === "ETIMEDOUT" ||
      error.message?.toLowerCase().includes("timeout") ||
      error.response?.status === 504 ||
      error.response?.status === 408;

    const isColdStart =
      error.response?.status === 502 ||
      error.response?.status === 503;

    const isNetworkError =
      error.code === "ERR_NETWORK" ||
      !error.response;

    error.isTimeout = isTimeout;
    error.isColdStart = isColdStart;
    error.isNetworkError = isNetworkError;

    if (isTimeout) {
      error.friendlyMessage =
        "Processing is taking longer than usual. The server may be waking up or the document is still being analyzed. Please wait or try again.";
    } else if (isColdStart) {
      error.friendlyMessage =
        "The server is currently waking up or temporarily unavailable. Please try again in a few seconds.";
    } else if (isNetworkError) {
      error.friendlyMessage =
        "Unable to connect to the server. The backend might be starting up. Please check your connection or try again.";
    } else {
      error.friendlyMessage =
        error.response?.data?.message || "An unexpected error occurred. Please try again.";
    }

    return Promise.reject(error);
  }
);

export default api;