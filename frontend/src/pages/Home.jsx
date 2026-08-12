import { Link } from "react-router-dom";
import { ArrowRight, FileText, LockKeyhole, ScanLine } from "lucide-react";

export default function Home() {
  return (
    <div className="landing">
      <div className="landing-nav"><div className="brand"><div className="brand-mark"><ScanLine size={21}/></div><strong>Smart Receipt</strong></div><div><Link className="link-btn" to="/login">Login</Link><Link className="primary-btn small" to="/register">Get started</Link></div></div>
      <section className="hero">
        <div className="eyebrow">OCR-powered receipt management</div>
        <h1>Turn paper receipts into <span>organized data.</span></h1>
        <p>Scan a receipt, extract its details automatically, manage your records and generate polished PDF reports.</p>
        <Link className="primary-btn" to="/register">Start scanning <ArrowRight size={18}/></Link>
      </section>
      <section className="feature-grid">
        <Feature icon={<ScanLine/>} title="OCR scanning" text="Extract merchant, date, items and totals from receipt images."/>
        <Feature icon={<LockKeyhole/>} title="Secure access" text="JWT authentication keeps your receipt data protected."/>
        <Feature icon={<FileText/>} title="PDF reports" text="Download clean, shareable receipt reports whenever you need them."/>
      </section>
    </div>
  );
}
function Feature({icon,title,text}){return <div className="feature-card"><div className="feature-icon">{icon}</div><h3>{title}</h3><p>{text}</p></div>}