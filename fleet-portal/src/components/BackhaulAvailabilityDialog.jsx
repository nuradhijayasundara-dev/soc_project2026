import React, { useEffect, useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, Button,
  Grid, MenuItem, Alert,
} from '@mui/material';
import { getTrucks } from '../api/truckApi';
import { postBackhaulAvailability } from '../api/availabilityApi';

/**
 * "Add truck availability for backhaul, available capacity, and return destination":
 * fleet manager picks which truck just finished an outbound delivery, and
 * posts its empty return leg — current location, return destination, and
 * how much capacity is free for that leg.
 */
export default function BackhaulAvailabilityDialog({ open, onClose, onPosted }) {
  const [trucks, setTrucks] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    truckId: '', routeFrom: '', returnDestination: '', availableFrom: '', availableCapacityTon: '',
  });

  useEffect(() => {
    if (open) getTrucks().then(setTrucks).catch(() => {});
  }, [open]);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await postBackhaulAvailability(form);
      setForm({ truckId: '', routeFrom: '', returnDestination: '', availableFrom: '', availableCapacityTon: '' });
      onPosted();
    } catch {
      setError('Could not post backhaul availability');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Post Backhaul Availability</DialogTitle>
      <form onSubmit={handleSubmit}>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Truck" value={form.truckId} onChange={set('truckId')} required>
                {trucks.map((t) => (
                  <MenuItem key={t.id} value={t.id}>{t.truckNo} — {t.capacityTon} ton</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="Current Location" value={form.routeFrom}
                onChange={set('routeFrom')} required helperText="Where the truck is after its delivery" />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="Return Destination" value={form.returnDestination}
                onChange={set('returnDestination')} required helperText="Where the empty return leg is headed" />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="datetime-local" label="Available From"
                InputLabelProps={{ shrink: true }} value={form.availableFrom}
                onChange={set('availableFrom')} required />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="number" label="Available Capacity (ton)"
                value={form.availableCapacityTon} onChange={set('availableCapacityTon')} required />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? 'Posting…' : 'Post Availability'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
