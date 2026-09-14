import React, { useEffect, useState } from "react";
import {
  Table, TableHead, TableRow, TableCell, TableBody, Paper, Chip, Button, Box, Typography,
} from "@mui/material";
import axiosClient from "../api/axiosClient";

const statusColor = {
  AVAILABLE: "success",
  BOOKED: "warning",
  EXPIRED: "default",
};

export default function TruckAvailability() {
  const [rows, setRows] = useState([]);

  useEffect(() => {
    axiosClient.get("/fleet/availability")
      .then((res) => setRows(res.data || []))
      .catch(() => {});
  }, []);

  return (
    <Paper variant="outlined">
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", p: 2 }}>
        <Typography variant="h6">Truck Availability</Typography>
        <Button variant="contained">+ Add Availability</Button>
      </Box>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Truck No</TableCell>
            <TableCell>Route</TableCell>
            <TableCell>Available Capacity</TableCell>
            <TableCell>Available From</TableCell>
            <TableCell>Status</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.availabilityId}>
              <TableCell>{row.truck?.truckNo}</TableCell>
              <TableCell>{row.routeFromCityId} → {row.routeToCityId}</TableCell>
              <TableCell>{row.availableCapacityTon} Ton</TableCell>
              <TableCell>{row.availableFrom}</TableCell>
              <TableCell><Chip size="small" label={row.status} color={statusColor[row.status] || "default"} /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
}
