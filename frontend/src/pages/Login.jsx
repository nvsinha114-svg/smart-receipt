import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ScanLine } from "lucide-react";
import { useAuth } from "../context/AuthContext";

export default function Login() {
  const { login } = useAuth(), navigate = useNavigate();
  const [form,setForm]=useState({email:"",password:""}), [error,setError]=useState(""), [loading,setLoading]=useState(false);
  const submit=async(e)=>{e.preventDefault();setError("");setLoading(true);try{await login(form.email,form.password);navigate("/dashboard")}catch(err){setError(err.response?.data?.message||err.message||"Invalid email or password")}finally{setLoading(false)}};
  return <AuthPage title="Welcome back" subtitle="Sign in to manage your receipts.">
    <form onSubmit={submit} className="auth-form">
      {error&&<div className="alert error">{error}</div>}
      <label>Email<input type="email" required value={form.email} onChange={e=>setForm({...form,email:e.target.value})}/></label>
      <label>Password<input type="password" required value={form.password} onChange={e=>setForm({...form,password:e.target.value})}/></label>
      <button className="primary-btn full" disabled={loading}>{loading?"Signing in...":"Sign in"}</button>
      <p className="auth-switch">Don't have an account? <Link to="/register">Create one</Link></p>
    </form>
  </AuthPage>
}
function AuthPage({title,subtitle,children}){return <div className="auth-page"><div className="auth-brand"><div className="brand-mark"><ScanLine/></div><strong>Smart Receipt</strong></div><div className="auth-card"><h1>{title}</h1><p>{subtitle}</p>{children}</div></div>}