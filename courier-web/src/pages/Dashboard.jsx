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
  const [stats, setStats] = useState({ shipments: 0, inTransit: 0, delivered: 0 });

  useEffect(() => {
    // Replace with real endpoint once courier-service stats API is ready
    axiosClient.get("/shipments")
      .then((res) => {
        const shipments = res.data || [];
        setStats({
          shipments: shipments.length,
          inTransit: shipments.filter((s) => s.status === "IN_TRANSIT").length,
          delivered: shipments.filter((s) => s.status === "DELIVERED").length,
        });
      })
      .catch(() => {
        // service not running yet during early development — keep zeros
      });
  }, []);

  return (
    <Box>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={4}><StatCard label="Total Shipments" value={stats.shipments} /></Grid>
        <Grid item xs={12} sm={4}><StatCard label="In Transit" value={stats.inTransit} /></Grid>
        <Grid item xs={12} sm={4}><StatCard label="Delivered" value={stats.delivered} /></Grid>
      </Grid>
    </Box>
  );
}
