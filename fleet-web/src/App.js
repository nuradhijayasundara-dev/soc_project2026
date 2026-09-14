import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { Box, CssBaseline, Toolbar } from "@mui/material";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import PrivateRoute from "./components/PrivateRoute";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import TruckList from "./pages/TruckList";
import TruckAvailability from "./pages/TruckAvailability";
import RouteGPS from "./pages/RouteGPS";

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
          <PrivateRoute><AppLayout title="Fleet Dashboard"><Dashboard /></AppLayout></PrivateRoute>
        } />
        <Route path="/trucks" element={
          <PrivateRoute><AppLayout title="Trucks"><TruckList /></AppLayout></PrivateRoute>
        } />
        <Route path="/availability" element={
          <PrivateRoute><AppLayout title="Truck Availability"><TruckAvailability /></AppLayout></PrivateRoute>
        } />
        <Route path="/bookings" element={
          <PrivateRoute><AppLayout title="Bookings"><Placeholder title="Bookings" /></AppLayout></PrivateRoute>
        } />
        <Route path="/gps" element={
          <PrivateRoute><AppLayout title="GPS Tracking"><RouteGPS /></AppLayout></PrivateRoute>
        } />
        <Route path="/drivers" element={
          <PrivateRoute><AppLayout title="Drivers"><Placeholder title="Drivers" /></AppLayout></PrivateRoute>
        } />
        <Route path="/reports" element={
          <PrivateRoute><AppLayout title="Reports"><Placeholder title="Reports" /></AppLayout></PrivateRoute>
        } />
        <Route path="/settings" element={
          <PrivateRoute><AppLayout title="Settings"><Placeholder title="Settings" /></AppLayout></PrivateRoute>
        } />
      </Routes>
    </BrowserRouter>
  );
}
