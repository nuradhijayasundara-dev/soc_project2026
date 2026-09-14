import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import ShipmentList from './pages/ShipmentList';
import ShipmentCreate from './pages/ShipmentCreate';
import ShipmentDetails from './pages/ShipmentDetails';
import Customers from './pages/Customers';
import Tracking from './pages/Tracking';

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
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/requests" element={<ShipmentCreate />} />
          <Route path="/shipments" element={<ShipmentList />} />
          <Route path="/shipments/:id" element={<ShipmentDetails />} />
          <Route path="/customers" element={<Customers />} />
          <Route path="/tracking" element={<Tracking />} />
          <Route path="/invoices" element={<div>Invoices &amp; Payments screen goes here</div>} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
