import React, { useEffect, useState } from 'react';
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Paper, Chip, CircularProgress, Button,
} from '@mui/material';
import { getDrivers } from '../api/driverApi';
import DriverRegisterDialog from '../components/DriverRegisterDialog';

const statusColor = { AVAILABLE: 'success', ON_TRIP: 'info', OFF_DUTY: 'default' };

export default function Drivers() {
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);

  const load = () => {
    setLoading(true);
    getDrivers()
      .then(setDrivers)
      .catch(() => setError('Could not load drivers (is fleet-service running?)'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Drivers</Typography>
        <Button variant="contained" onClick={() => setDialogOpen(true)}>Register Driver</Button>
      </Box>

      {loading && <CircularProgress />}
      {error && <Typography color="error">{error}</Typography>}

      {!loading && !error && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Phone</TableCell>
                <TableCell>License No.</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {drivers.length === 0 ? (
                <TableRow><TableCell colSpan={4}>No drivers registered yet.</TableCell></TableRow>
              ) : drivers.map((d) => (
                <TableRow key={d.id}>
                  <TableCell>{d.fullName}</TableCell>
                  <TableCell>{d.phone}</TableCell>
                  <TableCell>{d.licenseNo}</TableCell>
                  <TableCell>
                    <Chip label={d.status} color={statusColor[d.status] || 'default'} size="small" />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}

      <DriverRegisterDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onRegistered={() => { setDialogOpen(false); load(); }}
      />
    </Box>
  );
}
