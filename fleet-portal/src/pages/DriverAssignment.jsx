import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Paper, Chip, CircularProgress, Button, MenuItem, TextField, Alert,
} from '@mui/material';
import { getTrips, assignDriver } from '../api/tripApi';
import { getDrivers } from '../api/driverApi';

const statusColor = {
  SCHEDULED: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', CANCELLED: 'error',
};

export default function DriverAssignment() {
  const navigate = useNavigate();
  const [trips, setTrips] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [selectedDriver, setSelectedDriver] = useState({}); // tripId -> driverId
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = () => {
    setLoading(true);
    Promise.all([getTrips(), getDrivers(true)])
      .then(([t, d]) => { setTrips(t); setDrivers(d); })
      .catch(() => setError('Could not load trips (is fleet-service running?)'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const handleReassign = async (tripId) => {
    const driverId = selectedDriver[tripId];
    if (!driverId) return;
    try {
      await assignDriver(tripId, driverId);
      load();
    } catch {
      setError('Could not reassign driver — trip may have already started');
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Trips &amp; Driver Assignment</Typography>
        <Button variant="contained" onClick={() => navigate('/trips/new')}>Create Trip</Button>
      </Box>

      {loading && <CircularProgress />}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {!loading && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Trip ID</TableCell>
                <TableCell>Truck ID</TableCell>
                <TableCell>Driver ID</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Reassign Driver</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {trips.length === 0 ? (
                <TableRow><TableCell colSpan={5}>No trips yet.</TableCell></TableRow>
              ) : trips.map((t) => (
                <TableRow key={t.id}>
                  <TableCell>#{t.id}</TableCell>
                  <TableCell>{t.truckId}</TableCell>
                  <TableCell>{t.driverId}</TableCell>
                  <TableCell>
                    <Chip label={t.status} color={statusColor[t.status] || 'default'} size="small" />
                  </TableCell>
                  <TableCell>
                    {t.status === 'SCHEDULED' ? (
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <TextField
                          select size="small" sx={{ minWidth: 140 }}
                          value={selectedDriver[t.id] || ''}
                          onChange={(e) => setSelectedDriver((s) => ({ ...s, [t.id]: e.target.value }))}
                        >
                          {drivers.map((d) => (
                            <MenuItem key={d.id} value={d.id}>{d.fullName}</MenuItem>
                          ))}
                        </TextField>
                        <Button size="small" variant="outlined" onClick={() => handleReassign(t.id)}>
                          Assign
                        </Button>
                      </Box>
                    ) : '—'}
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
