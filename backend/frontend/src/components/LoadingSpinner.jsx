import React from 'react';

const LoadingSpinner = ({ message = 'Loading...' }) => {
  return (
    <div className="spinner-container">
      <div className="spinner"></div>
      {message && <p>{message}</p>}
    </div>
  );
};

export default LoadingSpinner;
