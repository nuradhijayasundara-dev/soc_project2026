import React, { useEffect, useState } from 'react';
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Paper, Chip, CircularProgress, Button,
} from '@mui/material';
import { getMyAvailability } from '../api/availabilityApi';
import BackhaulAvailabilityDialog from '../components/BackhaulAvailabilityDialog';

const statusColor = { AVAILABLE: 'success', BOOKED: 'info', EXPIRED: 'default' };
const tripTypeColor = { BACKHAUL: 'secondary', OUTBOUND: 'default' };

export default function Availability() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);

  const load = () => {
    setLoading(true);
    getMyAvailability()
      .then(setRows)
      .catch(() => setError('Could not load availability (is fleet-service running?)'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Truck Availability</Typography>
        <Button variant="contained" onClick={() => setDialogOpen(true)}>Post Backhaul Availability</Button>
      </Box>

      {loading && <CircularProgress />}
      {error && <Typography color="error">{error}</Typography>}

      {!loading && !error && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Truck ID</TableCell>
                <TableCell>From</TableCell>
                <TableCell>Return Destination</TableCell>
                <TableCell>Available From</TableCell>
                <TableCell>Capacity (ton)</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow><TableCell colSpan={7}>No availability posted yet.</TableCell></TableRow>
              ) : rows.map((a) => (
                <TableRow key={a.id}>
                  <TableCell>#{a.truckId}</TableCell>
                  <TableCell>{a.routeFrom}</TableCell>
                  <TableCell>{a.routeTo}</TableCell>
                  <TableCell>{a.availableFrom}</TableCell>
                  <TableCell>{a.availableCapacityTon}</TableCell>
                  <TableCell>
                    <Chip label={a.tripType} size="small" color={tripTypeColor[a.tripType] || 'default'} />
                  </TableCell>
                  <TableCell>
                    <Chip label={a.status} size="small" color={statusColor[a.status] || 'default'} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}

      <BackhaulAvailabilityDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onPosted={() => { setDialogOpen(false); load(); }}
      />
    </Box>
  );
}
