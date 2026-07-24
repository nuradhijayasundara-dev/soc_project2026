import React, { useState } from 'react';
import {
  Box, Typography, Paper, TextField, Button, Grid, Chip, Stepper, Step,
  StepLabel, Alert, CircularProgress,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { getShipments, getShipmentTracking } from '../api/shipmentApi';

const STATUS_FLOW = ['PENDING', 'MATCHED', 'IN_TRANSIT', 'DELIVERED'];
const STATUS_LABELS = {
  PENDING: 'Request Placed', MATCHED: 'Matched', IN_TRANSIT: 'In Transit', DELIVERED: 'Delivered',
};
const statusColor = {
  PENDING: 'warning', MATCHED: 'info', IN_TRANSIT: 'primary', DELIVERED: 'success', CANCELLED: 'error',
};

/**
 * "5. SHIPMENT TRACKING INTERFACE" from the interface map: search a shipment
 * by its code, see the shipment info panel + a progress stepper + its full
 * tracking history. (The live map pin shown in the mockup comes from
 * gps-service once a shipment is linked to a Trip — see fleet-portal's GPS
 * Tracking page for the map itself.)
 */
export default function Tracking() {
  const [code, setCode] = useState('');
  const [shipment, setShipment] = useState(null);
  const [tracking, setTracking] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [searched, setSearched] = useState(false);

  const handleSearch = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    setSearched(true);
    try {
      // courier-service doesn't expose a "find by code" endpoint yet, so we
      // filter the company's own shipment list client-side — swap this for a
      // GET /courier/shipments?code= once that endpoint exists.
      const all = await getShipments();
      const match = all.find((s) => s.shipmentCode.toLowerCase() === code.trim().toLowerCase());
      if (!match) {
        setShipment(null);
        setError(`No shipment found with code "${code}"`);
        return;
      }
      const history = await getShipmentTracking(match.id);
      setShipment(match);
      setTracking(history);
    } catch {
      setError('Could not search shipments (is courier-service running?)');
    } finally {
      setLoading(false);
    }
  };

  const currentStepIndex = shipment ? STATUS_FLOW.indexOf(shipment.status) : -1;

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>Shipment Tracking</Typography>

      <Paper sx={{ p: 3, mb: 3 }} component="form" onSubmit={handleSearch}>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <TextField
            fullWidth label="Shipment ID" placeholder="SHP-0001"
            value={code} onChange={(e) => setCode(e.target.value)} required
          />
          <Button type="submit" variant="contained" startIcon={<SearchIcon />} disabled={loading}>
            Track
          </Button>
        </Box>
      </Paper>

      {loading && <CircularProgress />}
      {!loading && error && <Alert severity="warning">{error}</Alert>}

      {!loading && shipment && (
        <>
          <Paper sx={{ p: 3, mb: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="h6">Shipment {shipment.shipmentCode}</Typography>
              <Chip label={shipment.status} color={statusColor[shipment.status] || 'default'} />
            </Box>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}><b>From:</b> {shipment.pickupLocation}</Grid>
              <Grid item xs={12} sm={6}><b>To:</b> {shipment.destination}</Grid>
              <Grid item xs={12} sm={6}><b>Weight:</b> {shipment.weightKg ?? '—'} kg</Grid>
              <Grid item xs={12} sm={6}><b>Est. Delivery:</b> {shipment.deliveryDeadline || '—'}</Grid>
            </Grid>
          </Paper>

          {shipment.status !== 'CANCELLED' && (
            <Paper sx={{ p: 3, mb: 3 }}>
              <Stepper activeStep={currentStepIndex} alternativeLabel>
                {STATUS_FLOW.map((s) => <Step key={s}><StepLabel>{STATUS_LABELS[s]}</StepLabel></Step>)}
              </Stepper>
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
        </>
      )}
    </Box>
  );
}
