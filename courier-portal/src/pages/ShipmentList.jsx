import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Paper, Chip, CircularProgress, Button,
} from '@mui/material';
import { getShipments } from '../api/shipmentApi';

const statusColor = {
  PENDING: 'warning', MATCHED: 'info', IN_TRANSIT: 'primary',
  DELIVERED: 'success', CANCELLED: 'error',
};

export default function ShipmentList() {
  const navigate = useNavigate();
  const [shipments, setShipments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getShipments()
      .then(setShipments)
      .catch(() => setError('Could not load shipments (is courier-service running?)'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Shipments</Typography>
        <Button variant="contained" onClick={() => navigate('/requests')}>New Shipment Request</Button>
      </Box>

      {loading && <CircularProgress />}
      {error && <Typography color="error">{error}</Typography>}

      {!loading && !error && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Shipment ID</TableCell>
                <TableCell>From</TableCell>
                <TableCell>To</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Date</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {shipments.length === 0 ? (
                <TableRow><TableCell colSpan={5}>No shipments yet.</TableCell></TableRow>
              ) : shipments.map((s) => (
                <TableRow
                  key={s.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/shipments/${s.id}`)}
                >
                  <TableCell>{s.shipmentCode}</TableCell>
                  <TableCell>{s.pickupLocation}</TableCell>
                  <TableCell>{s.destination}</TableCell>
                  <TableCell>
                    <Chip label={s.status} color={statusColor[s.status] || 'default'} size="small" />
                  </TableCell>
                  <TableCell>{s.createdAt?.slice(0, 10)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Box>
  );
}
