import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ScanLine } from "lucide-react";
import { useAuth } from "../context/AuthContext";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      // Step 1: validate credentials — backend sends login OTP, returns { requiresOtp: true, email }
      const data = await login(form.email.trim(), form.password);

      if (data?.requiresOtp) {
        // Navigate to OTP verification page — JWT is NOT stored yet
        navigate("/verify-otp", {
          state: { email: data.email || form.email.trim(), mode: "login" },
        });
      } else {
        // Fallback (should not happen with updated backend)
        setError("Unexpected login response. Please try again.");
      }
    } catch (err) {
      const msg = err.friendlyMessage || err.response?.data?.message || err.message || "Invalid email or password";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthPage title="Welcome back" subtitle="Sign in to manage your receipts.">
      <form onSubmit={submit} className="auth-form">
        {error && <div className="alert error">{error}</div>}

        <label>
          Email
          <input
            type="email"
            required
            placeholder="example@gmail.com"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
        </label>

        <label>
          Password
          <input
            type="password"
            required
            placeholder="••••••••"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
        </label>

        <button className="primary-btn full" disabled={loading}>
          {loading ? "Signing in..." : "Sign in"}
        </button>

        <p className="auth-switch">
          Don't have an account? <Link to="/register">Create one</Link>
        </p>
      </form>
    </AuthPage>
  );
}

function AuthPage({ title, subtitle, children }) {
  return (
    <div className="auth-page">
      <div className="auth-brand">
        <div className="brand-mark">
          <ScanLine />
        </div>
        <strong>Smart Receipt</strong>
      </div>
      <div className="auth-card">
        <h1>{title}</h1>
        <p>{subtitle}</p>
        {children}
      </div>
    </div>
  );
}