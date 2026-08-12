import React, { useEffect } from 'react';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';

const Toast = ({ message, type = 'info', onClose, duration = 4000 }) => {
  useEffect(() => {
    if (duration) {
      const timer = setTimeout(() => {
        onClose();
      }, duration);
      return () => clearTimeout(timer);
    }
  }, [duration, onClose]);

  if (!message) return null;

  const icons = {
    success: <CheckCircle2 size={20} color="var(--success)" />,
    error: <AlertTriangle size={20} color="var(--danger)" />,
    info: <Info size={20} color="var(--primary)" />,
  };

  return (
    <div className="toast-container">
      <div className={`toast toast-${type}`}>
        {icons[type] || icons.info}
        <span style={{ flex: 1, fontSize: '0.9rem', fontWeight: 500 }}>{message}</span>
        <button
          onClick={onClose}
          style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
        >
          <X size={16} />
        </button>
      </div>
    </div>
  );
};

export default Toast;
