import React, { useEffect, useState } from 'react';
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Paper, Chip, CircularProgress,
} from '@mui/material';
import { getTrucks } from '../api/truckApi';

const statusColor = { AVAILABLE: 'success', ON_TRIP: 'info', MAINTENANCE: 'warning' };

export default function TruckList() {
  const [trucks, setTrucks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getTrucks()
      .then(setTrucks)
      .catch(() => setError('Could not load trucks (is fleet-service running?)'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>Trucks</Typography>

      {loading && <CircularProgress />}
      {error && <Typography color="error">{error}</Typography>}

      {!loading && !error && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Truck No.</TableCell>
                <TableCell>Capacity (ton)</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {trucks.length === 0 ? (
                <TableRow><TableCell colSpan={4}>No trucks registered yet.</TableCell></TableRow>
              ) : trucks.map((t) => (
                <TableRow key={t.id}>
                  <TableCell>{t.truckNo}</TableCell>
                  <TableCell>{t.capacityTon}</TableCell>
                  <TableCell>{t.truckType}</TableCell>
                  <TableCell>
                    <Chip label={t.status} color={statusColor[t.status] || 'default'} size="small" />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Box>
  );
}
