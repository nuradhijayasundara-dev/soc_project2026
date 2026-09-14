import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Paper, TextField, Button, Typography, Alert } from "@mui/material";
import LocalShippingIcon from "@mui/icons-material/LocalShipping";
import axiosClient from "../api/axiosClient";

export default function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      // Goes through the API Gateway to auth-service — /api/auth/login
      const res = await axiosClient.post("/auth/login", { username, password });
      localStorage.setItem("token", res.data.token);
      localStorage.setItem("username", res.data.username);
      localStorage.setItem("roles", JSON.stringify(res.data.roles));
      navigate("/");
    } catch (err) {
      setError("Invalid username or password");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", alignItems: "center", justifyContent: "center", bgcolor: "#f1f5f9" }}>
      <Paper sx={{ p: 4, width: 360 }} elevation={2}>
        <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", mb: 2 }}>
          <LocalShippingIcon sx={{ fontSize: 40 }} />
          <Typography variant="h6" fontWeight={700}>Backhaul-Match</Typography>
          <Typography variant="body2" color="text.secondary">Fleet Management System — Sign in</Typography>
        </Box>

        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <form onSubmit={handleSubmit}>
          <TextField
            fullWidth
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            margin="normal"
            required
          />
          <TextField
            fullWidth
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            margin="normal"
            required
          />
          <Button
            fullWidth
            type="submit"
            variant="contained"
            sx={{ mt: 2 }}
            disabled={loading}
          >
            {loading ? "Signing in…" : "Login"}
          </Button>
        </form>
      </Paper>
    </Box>
  );
}
