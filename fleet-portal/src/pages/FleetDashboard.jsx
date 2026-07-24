import React, { useEffect, useState } from 'react';
import { Grid, Paper, Typography, Box } from '@mui/material';
import { getFleetDashboardSummary } from '../api/truckApi';

const StatCard = ({ label, value }) => (
  <Paper sx={{ p: 2 }} elevation={2}>
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Typography variant="h4" fontWeight={700}>{value}</Typography>
  </Paper>
);

export default function FleetDashboard() {
  const [summary, setSummary] = useState({
    trucksOnline: 0, availableCapacityTon: 0, todaysRevenue: 0, activeBookings: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getFleetDashboardSummary()
      .then(setSummary)
      .catch(() => setError('Could not load dashboard summary (is fleet-service running?)'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>Fleet Dashboard</Typography>
      {error && <Typography color="error" sx={{ mb: 2 }}>{error}</Typography>}
      {!loading && (
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Trucks Online" value={summary.trucksOnline} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Available Capacity (ton)" value={summary.availableCapacityTon} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Today's Revenue" value={summary.todaysRevenue} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Active Bookings" value={summary.activeBookings} />
          </Grid>
        </Grid>
      )}
    </Box>
  );
}
