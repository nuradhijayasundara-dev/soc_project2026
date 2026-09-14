import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { Box, CssBaseline, Toolbar } from "@mui/material";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import PrivateRoute from "./components/PrivateRoute";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import ShipmentList from "./pages/ShipmentList";

function Placeholder({ title }) {
  return <div>{title} — coming soon</div>;
}

function AppLayout({ title, children }) {
  return (
    <Box sx={{ display: "flex" }}>
      <CssBaseline />
      <Sidebar />
      <Box sx={{ flexGrow: 1 }}>
        <Topbar title={title} />
        <Toolbar />
        <Box sx={{ p: 3 }}>{children}</Box>
      </Box>
    </Box>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route path="/" element={
          <PrivateRoute><AppLayout title="Courier Dashboard"><Dashboard /></AppLayout></PrivateRoute>
        } />
        <Route path="/shipments" element={
          <PrivateRoute><AppLayout title="Shipments"><ShipmentList /></AppLayout></PrivateRoute>
        } />
        <Route path="/customers" element={
          <PrivateRoute><AppLayout title="Customers"><Placeholder title="Customers" /></AppLayout></PrivateRoute>
        } />
        <Route path="/bookings" element={
          <PrivateRoute><AppLayout title="Bookings"><Placeholder title="Bookings" /></AppLayout></PrivateRoute>
        } />
        <Route path="/notifications" element={
          <PrivateRoute><AppLayout title="Notifications"><Placeholder title="Notifications" /></AppLayout></PrivateRoute>
        } />
        <Route path="/settings" element={
          <PrivateRoute><AppLayout title="Settings"><Placeholder title="Settings" /></AppLayout></PrivateRoute>
        } />
      </Routes>
    </BrowserRouter>
  );
}
