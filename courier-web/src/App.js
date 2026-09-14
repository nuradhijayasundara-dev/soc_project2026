import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { Box, CssBaseline, Toolbar } from "@mui/material";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import Dashboard from "./pages/Dashboard";

// Placeholder pages — each member/page owner fills these in as their
// service's endpoints become available.
function Placeholder({ title }) {
  return <div>{title} — coming soon</div>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Box sx={{ display: "flex" }}>
        <CssBaseline />
        <Sidebar />
        <Box sx={{ flexGrow: 1 }}>
          <Topbar title="Courier Dashboard" />
          <Toolbar />
          <Box sx={{ p: 3 }}>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/shipments" element={<Placeholder title="Shipments" />} />
              <Route path="/customers" element={<Placeholder title="Customers" />} />
              <Route path="/bookings" element={<Placeholder title="Bookings" />} />
              <Route path="/notifications" element={<Placeholder title="Notifications" />} />
              <Route path="/settings" element={<Placeholder title="Settings" />} />
            </Routes>
          </Box>
        </Box>
      </Box>
    </BrowserRouter>
  );
}
