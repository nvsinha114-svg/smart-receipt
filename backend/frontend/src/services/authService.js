import api from './api';

export const authService = {
  register: async (name, email, password, role = 'USER') => {
    const response = await api.post('/api/auth/register', { name, email, password, role });
    return response.data;
  },

  login: async (email, password) => {
    const response = await api.post('/api/auth/login', { email, password });
    return response.data;
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  },

  getCurrentUser: () => {
    const userStr = localStorage.getItem('user');
    if (!userStr) return null;
    try {
      return JSON.parse(userStr);
    } catch {
      return null;
    }
  },

  getToken: () => localStorage.getItem('token'),
};

export default authService;
