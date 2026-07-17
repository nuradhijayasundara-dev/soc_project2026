import React, { useEffect, useState } from "react";
import {
  Table, TableHead, TableRow, TableCell, TableBody, Paper, Chip, Box, Typography, Button,
} from "@mui/material";
import axiosClient from "../api/axiosClient";

const statusColor = {
  AVAILABLE: "success",
  IN_TRANSIT: "warning",
  MAINTENANCE: "default",
  INACTIVE: "error",
};

export default function TruckList() {
  const [trucks, setTrucks] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axiosClient.get("/fleet/trucks")
      .then((res) => setTrucks(res.data || []))
      .catch(() => {
        // fleet-service not running yet during early development
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <Paper variant="outlined">
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", p: 2 }}>
        <Typography variant="h6">Trucks</Typography>
        <Button variant="contained">+ Add Truck</Button>
      </Box>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Truck No</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Capacity (Ton)</TableCell>
            <TableCell>Status</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {trucks.map((t) => (
            <TableRow key={t.truckId}>
              <TableCell>{t.truckNo}</TableCell>
              <TableCell>{t.truckType}</TableCell>
              <TableCell>{t.capacityTon}</TableCell>
              <TableCell><Chip size="small" label={t.status} color={statusColor[t.status] || "default"} /></TableCell>
            </TableRow>
          ))}
          {!loading && trucks.length === 0 && (
            <TableRow>
              <TableCell colSpan={4} align="center">No trucks added yet</TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </Paper>
  );
}
