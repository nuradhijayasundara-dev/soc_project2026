import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Paper, TextField, Button, Typography, Alert } from '@mui/material';
import { login } from '../api/authApi';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(username, password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data || 'Invalid username or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
        bgcolor: '#0d1b2a',
      }}
    >
      <Paper sx={{ p: 4, width: 380 }} elevation={6}>
        <Typography variant="h5" fontWeight={700} gutterBottom>Welcome Back!</Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Sign in to continue — Courier Portal
        </Typography>

        {error && <Alert severity="error" sx={{ my: 2 }}>{error}</Alert>}

        <Box component="form" onSubmit={handleSubmit} sx={{ mt: 2 }}>
          <TextField
            label="Username" fullWidth margin="normal" value={username}
            onChange={(e) => setUsername(e.target.value)} required
          />
          <TextField
            label="Password" type="password" fullWidth margin="normal" value={password}
            onChange={(e) => setPassword(e.target.value)} required
          />
          <Button type="submit" variant="contained" fullWidth sx={{ mt: 3 }} disabled={loading}>
            {loading ? 'Signing in…' : 'Login'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
