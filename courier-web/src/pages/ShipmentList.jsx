import React, { useEffect, useState } from "react";
import {
  Table, TableHead, TableRow, TableCell, TableBody, Paper, Chip, Box, Typography, Button,
} from "@mui/material";
import axiosClient from "../api/axiosClient";

const statusColor = {
  CREATED: "default",
  ASSIGNED: "info",
  IN_TRANSIT: "warning",
  DELIVERED: "success",
  CANCELLED: "error",
};

export default function ShipmentList() {
  const [shipments, setShipments] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axiosClient.get("/shipments")
      .then((res) => setShipments(res.data || []))
      .catch(() => {
        // courier-service not running yet during early development
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <Paper variant="outlined">
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", p: 2 }}>
        <Typography variant="h6">Shipments</Typography>
        <Button variant="contained">+ New Shipment</Button>
      </Box>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Shipment Code</TableCell>
            <TableCell>Receiver</TableCell>
            <TableCell>Pickup City</TableCell>
            <TableCell>Delivery City</TableCell>
            <TableCell>Weight (kg)</TableCell>
            <TableCell>Status</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {shipments.map((s) => (
            <TableRow key={s.shipmentId}>
              <TableCell>{s.shipmentCode}</TableCell>
              <TableCell>{s.receiver?.receiverName}</TableCell>
              <TableCell>{s.pickupCityId}</TableCell>
              <TableCell>{s.deliveryCityId}</TableCell>
              <TableCell>{s.totalWeightKg}</TableCell>
              <TableCell><Chip size="small" label={s.status} color={statusColor[s.status] || "default"} /></TableCell>
            </TableRow>
          ))}
          {!loading && shipments.length === 0 && (
            <TableRow>
              <TableCell colSpan={6} align="center">No shipments yet</TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </Paper>
  );
}
