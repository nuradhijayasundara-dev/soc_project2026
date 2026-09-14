import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { Box, CssBaseline, Toolbar } from "@mui/material";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import Dashboard from "./pages/Dashboard";
import TruckAvailability from "./pages/TruckAvailability";
import RouteGPS from "./pages/RouteGPS";

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
          <Topbar title="Fleet Dashboard" />
          <Toolbar />
          <Box sx={{ p: 3 }}>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/trucks" element={<Placeholder title="Trucks" />} />
              <Route path="/availability" element={<TruckAvailability />} />
              <Route path="/bookings" element={<Placeholder title="Bookings" />} />
              <Route path="/gps" element={<RouteGPS />} />
              <Route path="/drivers" element={<Placeholder title="Drivers" />} />
              <Route path="/reports" element={<Placeholder title="Reports" />} />
              <Route path="/settings" element={<Placeholder title="Settings" />} />
            </Routes>
          </Box>
        </Box>
      </Box>
    </BrowserRouter>
  );
}
