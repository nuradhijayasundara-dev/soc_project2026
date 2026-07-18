import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/Login';
import FleetDashboard from './pages/FleetDashboard';
import TruckList from './pages/TruckList';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route path="/dashboard" element={<FleetDashboard />} />
          <Route path="/trucks" element={<TruckList />} />
          <Route path="/availability" element={<div>Truck availability screen goes here</div>} />
          <Route path="/gps" element={<div>GPS tracking / map screen goes here</div>} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
