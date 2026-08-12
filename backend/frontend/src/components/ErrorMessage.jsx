import React from 'react';
import { AlertCircle } from 'lucide-react';

const ErrorMessage = ({ message, errors }) => {
  if (!message && (!errors || Object.keys(errors).length === 0)) return null;

  return (
    <div className="glass-card" style={{ borderLeft: '4px solid var(--danger)', marginBottom: '1.25rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <AlertCircle size={20} color="var(--danger)" />
        <span style={{ fontWeight: 600, color: 'var(--danger)' }}>{message || 'Validation Error'}</span>
      </div>

      {errors && typeof errors === 'object' && Object.keys(errors).length > 0 && (
        <ul style={{ marginTop: '0.5rem', paddingLeft: '2rem', fontSize: '0.875rem', color: 'var(--text-muted)' }}>
          {Object.entries(errors).map(([field, errMsg]) => (
            <li key={field}>
              <strong>{field}:</strong> {errMsg}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default ErrorMessage;
