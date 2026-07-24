import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Typography, Paper, Grid, TextField, Button, MenuItem, Alert, Divider,
} from '@mui/material';
import { createShipment } from '../api/shipmentApi';
import { getCustomers } from '../api/customerApi';

const PARCEL_TYPES = ['Documents', 'General Cargo', 'Fragile', 'Perishable'];

export default function ShipmentCreate() {
  const navigate = useNavigate();
  const [customers, setCustomers] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    customerId: '',
    newCustomerName: '',
    newCustomerPhone: '',
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',
    pickupLocation: '',
    pickupDatetime: '',
    destination: '',
    deliveryDeadline: '',
    weightKg: '',
    dimensions: '',
    parcelType: 'Documents',
    remarks: '',
  });

  useEffect(() => {
    getCustomers().then(setCustomers).catch(() => {});
  }, []);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const payload = {
        customerId: form.customerId || null,
        newCustomer: form.customerId
          ? null
          : { fullName: form.newCustomerName, phone: form.newCustomerPhone },
        receiver: {
          fullName: form.receiverName,
          phone: form.receiverPhone,
          address: form.receiverAddress,
        },
        pickupLocation: form.pickupLocation,
        pickupDatetime: form.pickupDatetime,
        destination: form.destination,
        deliveryDeadline: form.deliveryDeadline || null,
        weightKg: form.weightKg || null,
        dimensions: form.dimensions,
        parcelType: form.parcelType,
        remarks: form.remarks,
      };
      const shipment = await createShipment(payload);
      navigate(`/shipments/${shipment.id}`);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create shipment');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>New Shipment Request</Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Paper sx={{ p: 3 }} component="form" onSubmit={handleSubmit}>
        <Typography variant="subtitle1" fontWeight={600}>Sender</Typography>
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid item xs={12} sm={6}>
            <TextField
              select fullWidth label="Existing Customer (optional)"
              value={form.customerId} onChange={set('customerId')}
            >
              <MenuItem value="">— New customer —</MenuItem>
              {customers.map((c) => (
                <MenuItem key={c.id} value={c.id}>{c.fullName}</MenuItem>
              ))}
            </TextField>
          </Grid>
          {!form.customerId && (
            <>
              <Grid item xs={12} sm={3}>
                <TextField fullWidth label="Customer Name" value={form.newCustomerName}
                  onChange={set('newCustomerName')} required />
              </Grid>
              <Grid item xs={12} sm={3}>
                <TextField fullWidth label="Customer Phone" value={form.newCustomerPhone}
                  onChange={set('newCustomerPhone')} />
              </Grid>
            </>
          )}
        </Grid>

        <Divider sx={{ my: 2 }} />
        <Typography variant="subtitle1" fontWeight={600}>Pickup Details</Typography>
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Pickup Location" value={form.pickupLocation}
              onChange={set('pickupLocation')} required />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth type="datetime-local" label="Pickup Date & Time"
              InputLabelProps={{ shrink: true }} value={form.pickupDatetime}
              onChange={set('pickupDatetime')} required />
          </Grid>
        </Grid>

        <Divider sx={{ my: 2 }} />
        <Typography variant="subtitle1" fontWeight={600}>Delivery Details</Typography>
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Destination" value={form.destination}
              onChange={set('destination')} required />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth type="date" label="Delivery Deadline"
              InputLabelProps={{ shrink: true }} value={form.deliveryDeadline}
              onChange={set('deliveryDeadline')} />
          </Grid>
          <Grid item xs={12} sm={4}>
            <TextField fullWidth label="Receiver Name" value={form.receiverName}
              onChange={set('receiverName')} required />
          </Grid>
          <Grid item xs={12} sm={4}>
            <TextField fullWidth label="Receiver Phone" value={form.receiverPhone}
              onChange={set('receiverPhone')} />
          </Grid>
          <Grid item xs={12} sm={4}>
            <TextField fullWidth label="Receiver Address" value={form.receiverAddress}
              onChange={set('receiverAddress')} />
          </Grid>
        </Grid>

        <Divider sx={{ my: 2 }} />
        <Typography variant="subtitle1" fontWeight={600}>Parcel Details</Typography>
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={3}>
            <TextField fullWidth type="number" label="Weight (kg)" value={form.weightKg}
              onChange={set('weightKg')} />
          </Grid>
          <Grid item xs={12} sm={3}>
            <TextField fullWidth label="Dimensions (cm)" value={form.dimensions}
              onChange={set('dimensions')} placeholder="30 x 20 x 20" />
          </Grid>
          <Grid item xs={12} sm={3}>
            <TextField select fullWidth label="Parcel Type" value={form.parcelType}
              onChange={set('parcelType')}>
              {PARCEL_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth multiline rows={2} label="Remarks (optional)"
              value={form.remarks} onChange={set('remarks')} />
          </Grid>
        </Grid>

        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button type="button" variant="outlined" onClick={() => navigate('/shipments')}>Reset</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? 'Submitting…' : 'Submit Request'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
