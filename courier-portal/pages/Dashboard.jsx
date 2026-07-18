import React, { useEffect, useState } from 'react';
import { Grid, Paper, Typography, Box } from '@mui/material';
import { getDashboardSummary } from '../api/shipmentApi';

const StatCard = ({ label, value, sub }) => (
  <Paper sx={{ p: 2 }} elevation={2}>
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Typography variant="h4" fontWeight={700}>{value}</Typography>
    {sub && <Typography variant="caption" color="success.main">{sub}</Typography>}
  </Paper>
);

export default function Dashboard() {
  const [summary, setSummary] = useState({
    totalRequests: 0, matchedShipments: 0, inTransit: 0, delivered: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getDashboardSummary()
      .then(setSummary)
      .catch(() => setError('Could not load dashboard summary (is courier-service running?)'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>Dashboard</Typography>
      {error && <Typography color="error" sx={{ mb: 2 }}>{error}</Typography>}
      {!loading && (
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Total Requests" value={summary.totalRequests} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Matched Shipments" value={summary.matchedShipments} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="In Transit" value={summary.inTransit} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Delivered" value={summary.delivered} />
          </Grid>
        </Grid>
      )}
    </Box>
  );
}
