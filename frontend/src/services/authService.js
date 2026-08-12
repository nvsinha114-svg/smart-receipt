import api from "./api";

export const register = (payload) => api.post("/api/auth/register", payload);
export const login = (payload) => api.post("/api/auth/login", payload);