import api from "./api";

export const register = (payload) => api.post("/api/auth/register", payload);
export const verifyOtp = (payload) => api.post("/api/auth/verify-otp", payload);
export const resendOtp = (payload) => api.post("/api/auth/resend-otp", payload);
export const login = (payload) => api.post("/api/auth/login", payload);
export const verifyLoginOtp = (payload) => api.post("/api/auth/verify-login-otp", payload);
export const resendLoginOtp = (payload) => api.post("/api/auth/resend-login-otp", payload);