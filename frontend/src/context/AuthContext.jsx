import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { login as loginRequest } from "../services/authService";

const AuthContext = createContext(null);

function decodeJwt(token) {
  try {
    const payload = token.split(".")[1];
    return JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem("smart_receipt_token"));
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem("smart_receipt_user")); }
    catch { return null; }
  });

  useEffect(() => {
    const handler = () => logout();
    window.addEventListener("auth-expired", handler);
    return () => window.removeEventListener("auth-expired", handler);
  }, []);

  /**
   * Step 1 of login — calls the backend to validate credentials and send login OTP.
   * Returns { requiresOtp: true, email } — does NOT store JWT.
   */
  const login = async (email, password) => {
    const { data } = await loginRequest({ email, password });
    // Backend now returns { requiresOtp: true, email, message } — NOT a JWT
    return data;
  };

  /**
   * Step 2 of login — called after OTP is verified.
   * Stores the JWT and user info, granting access to protected routes.
   */
  const loginWithToken = (data) => {
    const jwt = data?.token || data?.accessToken || data?.jwt;
    if (!jwt) throw new Error("No JWT token returned by the backend.");

    const claims = decodeJwt(jwt);
    const currentUser = data?.user || {
      name: claims?.name || claims?.username || data?.email?.split("@")[0],
      email: claims?.email || data?.email,
      role: claims?.role || claims?.roles?.[0] || "USER",
    };

    localStorage.setItem("smart_receipt_token", jwt);
    localStorage.setItem("smart_receipt_user", JSON.stringify(currentUser));
    setToken(jwt);
    setUser(currentUser);
  };

  const logout = () => {
    localStorage.removeItem("smart_receipt_token");
    localStorage.removeItem("smart_receipt_user");
    setToken(null);
    setUser(null);
  };

  const value = useMemo(
    () => ({ token, user, login, loginWithToken, logout, isAuthenticated: !!token }),
    [token, user]
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);