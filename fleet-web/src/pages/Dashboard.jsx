import React, { useEffect, useState } from "react";
import { Grid, Paper, Typography, Box } from "@mui/material";
import axiosClient from "../api/axiosClient";

function StatCard({ label, value }) {
  return (
    <Paper sx={{ p: 2, borderRadius: 2 }} variant="outlined">
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h5" fontWeight={700}>{value}</Typography>
    </Paper>
  );
}

export default function Dashboard() {
  const [trucks, setTrucks] = useState([]);

  useEffect(() => {
    axiosClient.get("/fleet/availability")
      .then((res) => setTrucks(res.data || []))
      .catch(() => {
        // fleet-service not running yet during early development
      });
  }, []);

  const available = trucks.filter((t) => t.status === "AVAILABLE").length;
  const totalCapacity = trucks.reduce((sum, t) => sum + (t.availableCapacityTon || 0), 0);

  return (
    <Box>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={3}><StatCard label="Trucks Online" value={trucks.length} /></Grid>
        <Grid item xs={12} sm={3}><StatCard label="Available Capacity" value={`${totalCapacity} Ton`} /></Grid>
        <Grid item xs={12} sm={3}><StatCard label="Available Trucks" value={available} /></Grid>
        <Grid item xs={12} sm={3}><StatCard label="Active Bookings" value="—" /></Grid>
      </Grid>
    </Box>
  );
}
