import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ScanLine, Mail, KeyRound, RefreshCw, CheckCircle2, ArrowLeft } from "lucide-react";
import { register, verifyOtp, resendOtp } from "../services/authService";

export default function Register() {
  const navigate = useNavigate();

  // Registration state: "FORM" -> "OTP"
  const [step, setStep] = useState("FORM");

  const [form, setForm] = useState({ name: "", email: "", password: "", confirmPassword: "" });
  const [otp, setOtp] = useState("");

  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  const [loading, setLoading] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    let timer;
    if (cooldown > 0) {
      timer = setInterval(() => setCooldown((prev) => prev - 1), 1000);
    }
    return () => clearInterval(timer);
  }, [cooldown]);

  const validateEmail = (email) => {
    if (!email || !email.trim()) return "Email is required.";
    const normalized = email.trim();

    // Strict email format regex
    const regex = /^(?=.{1,64}@)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}$/;
    if (!regex.test(normalized)) {
      return "Invalid email address format (e.g. example@gmail.com).";
    }

    const parts = normalized.split("@");
    if (parts.length !== 2) return "Invalid email format.";

    const localPart = parts[0];
    const domainPart = parts[1].toLowerCase();

    if (localPart.startsWith(".") || localPart.endsWith(".") || localPart.includes("..")) {
      return "Invalid email format.";
    }

    const lastDotIndex = domainPart.lastIndexOf(".");
    if (lastDotIndex <= 0 || lastDotIndex === domainPart.length - 1) {
      return "Invalid email domain format.";
    }

    const tld = domainPart.substring(lastDotIndex + 1);
    if (!/^[a-zA-Z]{2,63}$/.test(tld)) {
      return "Invalid email domain extension.";
    }

    if (domainPart.startsWith("gmail") && domainPart !== "gmail.com") {
      return "Gmail addresses must end with @gmail.com.";
    }

    return null;
  };

  const handleInitiateRegistration = async (e) => {
    e.preventDefault();
    setError("");
    setSuccessMsg("");

    if (form.password !== form.confirmPassword) {
      return setError("Passwords do not match.");
    }

    const emailErr = validateEmail(form.email);
    if (emailErr) {
      return setError(emailErr);
    }

    setLoading(true);
    try {
      const response = await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
      });

      setSuccessMsg(response.data?.message || "Verification code sent to your email!");
      setStep("OTP");
      setCooldown(60);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || "Registration failed. Please check your details.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setError("");
    setSuccessMsg("");

    if (!otp || otp.trim().length !== 6) {
      return setError("Please enter a valid 6-digit OTP code.");
    }

    setLoading(true);
    try {
      await verifyOtp({
        email: form.email.trim(),
        otp: otp.trim(),
      });

      setSuccessMsg("Account created and email verified! Redirecting to login...");
      setTimeout(() => {
        navigate("/login", { state: { email: form.email, verified: true } });
      }, 1500);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || "OTP verification failed. Please try again.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (cooldown > 0 || resendLoading) return;
    setError("");
    setSuccessMsg("");
    setResendLoading(true);

    try {
      const response = await resendOtp({ email: form.email.trim() });
      setSuccessMsg(response.data?.message || "A new 6-digit OTP code has been sent to your email.");
      setCooldown(60);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || "Failed to resend OTP code.";
      setError(msg);
    } finally {
      setResendLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-brand">
        <div className="brand-mark">
          <ScanLine />
        </div>
        <strong>Smart Receipt</strong>
      </div>

      <div className="auth-card">
        {step === "FORM" ? (
          <>
            <h1>Create Account</h1>
            <p>Enter your details to receive an OTP verification code.</p>

            <form onSubmit={handleInitiateRegistration} className="auth-form">
              {error && <div className="alert error">{error}</div>}
              {successMsg && <div className="alert success">{successMsg}</div>}

              <label>
                Full Name
                <input
                  type="text"
                  required
                  placeholder="John Doe"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </label>

              <label>
                Email Address
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
                  minLength="6"
                  required
                  placeholder="••••••••"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                />
              </label>

              <label>
                Confirm Password
                <input
                  type="password"
                  required
                  placeholder="••••••••"
                  value={form.confirmPassword}
                  onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
                />
              </label>

              <button className="primary-btn full" disabled={loading}>
                {loading ? "Sending OTP..." : "Send Verification Code"}
              </button>

              <p className="auth-switch">
                Already have an account? <Link to="/login">Sign in</Link>
              </p>
            </form>
          </>
        ) : (
          <>
            <div style={{ textAlign: "center", marginBottom: "1rem" }}>
              <Mail style={{ width: 44, height: 44, color: "var(--primary-color, #4f46e5)" }} />
            </div>
            <h1>Email Verification</h1>
            <p>
              We've sent a 6-digit verification code to <strong style={{ color: "#374151" }}>{form.email}</strong>.
            </p>

            <form onSubmit={handleVerifyOtp} className="auth-form">
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
                {loading ? "Verifying..." : "Verify & Complete Account"}
              </button>

              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "1rem" }}>
                <button
                  type="button"
                  onClick={() => {
                    setStep("FORM");
                    setError("");
                    setSuccessMsg("");
                  }}
                  style={{
                    background: "none",
                    border: "none",
                    color: "#6b7280",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    gap: "0.25rem",
                    fontSize: "0.9rem",
                  }}
                >
                  <ArrowLeft size={16} /> Back to details
                </button>

                <button
                  type="button"
                  disabled={cooldown > 0 || resendLoading}
                  onClick={handleResendOtp}
                  style={{
                    background: "none",
                    border: "none",
                    color: cooldown > 0 ? "#9ca3af" : "#4f46e5",
                    cursor: cooldown > 0 ? "not-allowed" : "pointer",
                    fontWeight: 600,
                    fontSize: "0.9rem",
                  }}
                >
                  {resendLoading ? "Resending..." : cooldown > 0 ? `Resend in ${cooldown}s` : "Resend OTP"}
                </button>
              </div>
            </form>
          </>
        )}
      </div>
    </div>
  );
}