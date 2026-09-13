import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Paper, Chip, CircularProgress, Button,
} from '@mui/material';
import { getTrucks } from '../api/truckApi';
import TruckRegisterDialog from '../components/TruckRegisterDialog';

const statusColor = { AVAILABLE: 'success', ON_TRIP: 'info', MAINTENANCE: 'warning' };

export default function TruckList() {
  const navigate = useNavigate();
  const [trucks, setTrucks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);

  const load = () => {
    setLoading(true);
    getTrucks()
      .then(setTrucks)
      .catch(() => setError('Could not load trucks (is fleet-service running?)'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Trucks</Typography>
        <Button variant="contained" onClick={() => setDialogOpen(true)}>Register Truck</Button>
      </Box>

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
                <TableRow
                  key={t.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/trucks/${t.id}`)}
                >
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

      <TruckRegisterDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onRegistered={() => { setDialogOpen(false); load(); }}
      />
    </Box>
  );
}
