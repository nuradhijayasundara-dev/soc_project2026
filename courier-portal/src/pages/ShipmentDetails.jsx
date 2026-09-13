import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  Box, Typography, Paper, Grid, Chip, Button, MenuItem, TextField,
  Stepper, Step, StepLabel, CircularProgress, Alert,
} from '@mui/material';
import { getShipmentById, getShipmentTracking, updateShipmentStatus } from '../api/shipmentApi';
import RequestBackhaulDialog from '../components/RequestBackhaulDialog';

const STATUS_FLOW = ['PENDING', 'MATCHED', 'IN_TRANSIT', 'DELIVERED'];

const statusColor = {
  PENDING: 'warning', MATCHED: 'info', IN_TRANSIT: 'primary',
  DELIVERED: 'success', CANCELLED: 'error',
};

export default function ShipmentDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [shipment, setShipment] = useState(null);
  const [tracking, setTracking] = useState([]);
  const [nextStatus, setNextStatus] = useState('');
  const [location, setLocation] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updating, setUpdating] = useState(false);
  const [matchDialogOpen, setMatchDialogOpen] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([getShipmentById(id), getShipmentTracking(id)])
      .then(([s, t]) => { setShipment(s); setTracking(t); })
      .catch(() => setError('Could not load shipment'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);

  const handleUpdateStatus = async () => {
    if (!nextStatus) return;
    setUpdating(true);
    try {
      await updateShipmentStatus(id, nextStatus, location);
      setNextStatus('');
      setLocation('');
      load();
    } catch {
      setError('Could not update status');
    } finally {
      setUpdating(false);
    }
  };

  if (loading) return <CircularProgress />;
  if (error) return <Alert severity="error">{error}</Alert>;
  if (!shipment) return null;

  const currentStepIndex = STATUS_FLOW.indexOf(shipment.status);

  return (
    <Box>
      <Button component={Link} to="/shipments" sx={{ mb: 2 }}>&larr; Back to Shipments</Button>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Typography variant="h5" fontWeight={700}>
          Shipment {shipment.shipmentCode}
          <Chip sx={{ ml: 2 }} label={shipment.status} color={statusColor[shipment.status] || 'default'} />
        </Typography>

        {shipment.status === 'PENDING' && (
          <Button variant="contained" color="secondary" onClick={() => setMatchDialogOpen(true)}>
            Request Backhaul Transport
          </Button>
        )}
      </Box>

      <RequestBackhaulDialog
        open={matchDialogOpen}
        onClose={() => setMatchDialogOpen(false)}
        shipment={shipment}
        onMatched={(requestId) => navigate(`/matches/${requestId}`)}
      />

      <Paper sx={{ p: 3, mb: 3, mt: 2 }}>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6}><b>From:</b> {shipment.pickupLocation}</Grid>
          <Grid item xs={12} sm={6}><b>To:</b> {shipment.destination}</Grid>
          <Grid item xs={12} sm={6}><b>Pickup:</b> {shipment.pickupDatetime}</Grid>
          <Grid item xs={12} sm={6}><b>Deadline:</b> {shipment.deliveryDeadline || '—'}</Grid>
          <Grid item xs={12} sm={6}><b>Weight:</b> {shipment.weightKg ?? '—'} kg</Grid>
          <Grid item xs={12} sm={6}><b>Parcel Type:</b> {shipment.parcelType || '—'}</Grid>
        </Grid>
      </Paper>

      {shipment.status !== 'CANCELLED' && shipment.status !== 'DELIVERED' && (
        <Paper sx={{ p: 3, mb: 3 }}>
          <Stepper activeStep={currentStepIndex} sx={{ mb: 3 }}>
            {STATUS_FLOW.map((s) => <Step key={s}><StepLabel>{s}</StepLabel></Step>)}
          </Stepper>

          <Typography variant="subtitle1" fontWeight={600} gutterBottom>Update Status</Typography>
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
            <TextField select label="New Status" value={nextStatus}
              onChange={(e) => setNextStatus(e.target.value)} sx={{ minWidth: 180 }}>
              {['PENDING', 'MATCHED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED']
                .filter((s) => s !== shipment.status)
                .map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </TextField>
            <TextField label="Location (optional)" value={location}
              onChange={(e) => setLocation(e.target.value)} />
            <Button variant="contained" onClick={handleUpdateStatus} disabled={!nextStatus || updating}>
              {updating ? 'Updating…' : 'Update'}
            </Button>
          </Box>
        </Paper>
      )}

      <Paper sx={{ p: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Tracking History</Typography>
        {tracking.length === 0 ? (
          <Typography color="text.secondary">No tracking events yet.</Typography>
        ) : (
          tracking.map((t) => (
            <Box key={t.id} sx={{ py: 1, borderBottom: '1px solid #eee' }}>
              <b>{t.status}</b> — {t.location || 'unknown location'} — {t.updatedAt}
            </Box>
          ))
        )}
      </Paper>
    </Box>
  );
}
