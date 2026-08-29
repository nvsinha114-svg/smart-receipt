import { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { ScanLine, Mail, KeyRound, RefreshCw } from "lucide-react";
import { verifyLoginOtp, resendLoginOtp, verifyOtp, resendOtp } from "../services/authService";
import { useAuth } from "../context/AuthContext";

/**
 * Unified OTP verification page.
 *
 * Accessed with location.state = { email, mode }
 *   mode = "login"        → verifies login OTP, stores JWT, goes to /dashboard
 *   mode = "registration" → (fallback) verifies registration OTP, goes to /login
 *
 * If accessed directly without state, redirects to /login.
 */
export default function VerifyOtp() {
  const { loginWithToken } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const email = location.state?.email || "";
  const mode = location.state?.mode || "login"; // "login" | "registration"

  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");
  const [loading, setLoading] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [cooldown, setCooldown] = useState(60);

  // If no email in state, redirect back to login
  useEffect(() => {
    if (!email) {
      navigate("/login", { replace: true });
    }
  }, [email, navigate]);

  // Cooldown timer
  useEffect(() => {
    let timer;
    if (cooldown > 0) {
      timer = setInterval(() => setCooldown((prev) => prev - 1), 1000);
    }
    return () => clearInterval(timer);
  }, [cooldown]);

  const isLoginMode = mode === "login";
  const title = isLoginMode ? "Verify Your Login" : "Verify Your Email";
  const subtitle = isLoginMode
    ? "Enter the OTP sent to your email to continue."
    : "Enter the OTP sent to your email to complete registration.";

  const handleVerify = async (e) => {
    e.preventDefault();
    setError("");
    setSuccessMsg("");

    if (!otp || otp.trim().length !== 6) {
      return setError("Please enter a valid 6-digit OTP code.");
    }

    setLoading(true);
    try {
      if (isLoginMode) {
        // Verify login OTP → get JWT
        const { data } = await verifyLoginOtp({ email, otp: otp.trim() });
        loginWithToken(data); // stores JWT + user in localStorage & state
        navigate("/dashboard", { replace: true });
      } else {
        // Verify registration OTP → account created
        await verifyOtp({ email, otp: otp.trim() });
        setSuccessMsg("Email verified! Redirecting to sign in...");
        setTimeout(() => {
          navigate("/login", { state: { email, verified: true }, replace: true });
        }, 1500);
      }
    } catch (err) {
      const msg = err.friendlyMessage || err.response?.data?.message || err.message || "OTP verification failed. Please try again.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (cooldown > 0 || resendLoading) return;
    setError("");
    setSuccessMsg("");
    setResendLoading(true);

    try {
      if (isLoginMode) {
        const { data } = await resendLoginOtp({ email });
        setSuccessMsg(data?.message || "A new login OTP has been sent to your email.");
      } else {
        const { data } = await resendOtp({ email });
        setSuccessMsg(data?.message || "A new OTP has been sent to your email.");
      }
      setCooldown(60);
    } catch (err) {
      const msg = err.friendlyMessage || err.response?.data?.message || err.message || "Failed to resend OTP. Please try again.";
      setError(msg);
    } finally {
      setResendLoading(false);
    }
  };

  if (!email) return null; // redirecting

  return (
    <div className="auth-page">
      <div className="auth-brand">
        <div className="brand-mark">
          <ScanLine />
        </div>
        <strong>Smart Receipt</strong>
      </div>

      <div className="auth-card">
        <div style={{ textAlign: "center", marginBottom: "1rem" }}>
          <Mail style={{ width: 44, height: 44, color: "var(--primary-color, #4f46e5)" }} />
        </div>

        <h1>{title}</h1>
        <p>
          {subtitle}
          <br />
          <strong style={{ color: "#374151" }}>{email}</strong>
        </p>

        <form onSubmit={handleVerify} className="auth-form">
          {error && <div className="alert error">{error}</div>}
          {successMsg && <div className="alert success">{successMsg}</div>}

          <label>
            Enter 6-Digit OTP Code
            <input
              type="text"
              maxLength="6"
              pattern="\d{6}"
              required
              placeholder="123456"
              autoFocus
              style={{
                fontSize: "1.35rem",
                letterSpacing: "0.4rem",
                textAlign: "center",
                fontWeight: "bold",
              }}
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
            />
          </label>

          <button className="primary-btn full" disabled={loading}>
            {loading ? "Verifying..." : isLoginMode ? "Verify & Sign In" : "Verify & Complete Account"}
          </button>

          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginTop: "1rem",
            }}
          >
            <Link
              to="/login"
              style={{ color: "#6b7280", fontSize: "0.9rem", textDecoration: "none" }}
            >
              ← Back to sign in
            </Link>

            <button
              type="button"
              disabled={cooldown > 0 || resendLoading}
              onClick={handleResend}
              style={{
                background: "none",
                border: "none",
                color: cooldown > 0 ? "#9ca3af" : "#4f46e5",
                cursor: cooldown > 0 ? "not-allowed" : "pointer",
                fontWeight: 600,
                fontSize: "0.9rem",
              }}
            >
              {resendLoading
                ? "Resending..."
                : cooldown > 0
                ? `Resend in ${cooldown}s`
                : "Resend OTP"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
