import { NavLink, Outlet } from "react-router-dom";
import { FileText, LayoutDashboard, LogOut, PlusCircle, UserRound, ScanLine, Activity } from "lucide-react";
import { useAuth } from "../context/AuthContext";

export default function Layout() {
  const { user, logout } = useAuth();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark"><ScanLine size={21}/></div>
          <div><strong>Smart Receipt</strong><span>Document Scanner</span></div>
        </div>
        <nav>
          <NavLink to="/dashboard"><LayoutDashboard size={18}/> Dashboard</NavLink>
          <NavLink to="/upload"><PlusCircle size={18}/> Scan Document</NavLink>
          <NavLink to="/receipts"><FileText size={18}/> My Receipts</NavLink>
          <NavLink to="/medical-reports"><Activity size={18}/> Medical Reports</NavLink>
          <NavLink to="/profile"><UserRound size={18}/> Profile</NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="user-mini">
            <div className="avatar">{(user?.name || "U")[0].toUpperCase()}</div>
            <div><b>{user?.name || "User"}</b><small>{user?.role || "USER"}</small></div>
          </div>
          <button className="ghost-btn full" onClick={logout}><LogOut size={17}/> Logout</button>
        </div>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}