import apiClient from './apiClient';

export const login = async (username, password) => {
  const { data } = await apiClient.post('/auth/login', { username, password });
  localStorage.setItem('token', data.token);
  localStorage.setItem('role', data.role);
  return data;
};

export const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('role');
};

export const isAuthenticated = () => !!localStorage.getItem('token');
