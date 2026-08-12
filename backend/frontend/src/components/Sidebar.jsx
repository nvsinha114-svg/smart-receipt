import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LayoutDashboard, UploadCloud, ReceiptText, User, ShieldCheck } from 'lucide-react';

const Sidebar = () => {
  const { isAdmin } = useAuth();

  const navItems = [
    { label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
    { label: 'Upload Receipt', path: '/upload', icon: UploadCloud },
    { label: 'My Receipts', path: '/receipts', icon: ReceiptText },
    { label: 'Profile', path: '/profile', icon: User },
  ];

  if (isAdmin) {
    navItems.push({ label: 'Admin Panel', path: '/admin', icon: ShieldCheck });
  }

  return (
    <aside style={{
      width: '240px',
      backgroundColor: 'var(--bg-sidebar)',
      borderRight: '1px solid var(--border-color)',
      padding: '1.5rem 1rem',
      display: 'flex',
      flexDirection: 'column',
      gap: '0.5rem',
      flexShrink: 0
    }}>
      <div style={{ padding: '0 0.75rem 1rem 0.75rem', fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-dim)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
        Navigation
      </div>

      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.path}
            to={item.path}
            style={({ isActive }) => ({
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              padding: '0.75rem 1rem',
              borderRadius: 'var(--radius-md)',
              fontSize: '0.9rem',
              fontWeight: 600,
              color: isActive ? '#ffffff' : 'var(--text-muted)',
              backgroundColor: isActive ? 'var(--primary-light)' : 'transparent',
              border: isActive ? '1px solid rgba(99, 102, 241, 0.3)' : '1px solid transparent',
              transition: 'var(--transition)'
            })}
          >
            <Icon size={18} />
            <span>{item.label}</span>
          </NavLink>
        );
      })}
    </aside>
  );
};

export default Sidebar;
