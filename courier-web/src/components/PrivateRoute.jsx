import React from "react";
import { Navigate } from "react-router-dom";

// Simple guard: no token in localStorage → bounce to /login.
// Replace with a proper auth context if the app grows past this stage.
export default function PrivateRoute({ children }) {
  const token = localStorage.getItem("token");
  return token ? children : <Navigate to="/login" replace />;
}
