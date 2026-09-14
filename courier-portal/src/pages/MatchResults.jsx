import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Box, Typography, Paper, Button, Grid, Chip, CircularProgress, Alert, Divider,
} from '@mui/material';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import RouteIcon from '@mui/icons-material/Route';
import { getMatchRequest, getMatchResults, acceptMatch } from '../api/matchingApi';

const statusColor = {
  RECOMMENDED: 'default',
  PENDING_CONFIRMATION: 'warning',
  ACCEPTED: 'success',
  REJECTED: 'default',
};
const statusLabel = {
  RECOMMENDED: null, // no badge needed for the default state
  PENDING_CONFIRMATION: 'Awaiting fleet confirmation',
  ACCEPTED: 'Booking confirmed',
  REJECTED: 'Declined',
};

export default function MatchResults() {
  const { requestId } = useParams();
  const [request, setRequest] = useState(null);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [acceptingId, setAcceptingId] = useState(null);

  const load = () => {
    setLoading(true);
    Promise.all([getMatchRequest(requestId), getMatchResults(requestId)])
      .then(([req, res]) => { setRequest(req); setResults(res); })
      .catch(() => setError('Could not load match results (is matching-service running?)'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [requestId]);

  const handleAcceptMatch = async (resultId) => {
    setAcceptingId(resultId);
    try {
      await acceptMatch(resultId);
      load();
    } catch {
      setError('Could not accept this match');
    } finally {
      setAcceptingId(null);
    }
  };

  if (loading) return <CircularProgress />;

  return (
    <Box>
      <Button component={Link} to={`/shipments/${request?.shipmentId || ''}`} sx={{ mb: 2 }}>
        &larr; Back to Shipment
      </Button>

      <Typography variant="h5" fontWeight={700} gutterBottom>Match Results</Typography>

      {request && (
        <Paper sx={{ p: 2, mb: 3 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={4}><b>From:</b> {request.pickupLocation}</Grid>
            <Grid item xs={12} sm={4}><b>To:</b> {request.destination}</Grid>
            <Grid item xs={12} sm={4}><b>Weight:</b> {request.weightKg} kg</Grid>
          </Grid>
        </Paper>
      )}

      {error && <Alert severity="warning" sx={{ mb: 2 }}>{error}</Alert>}

      {results.length === 0 ? (
        <Alert severity="info">
          No backhaul trucks are available on this route right now. Try again once a fleet
          manager posts availability for this lane.
        </Alert>
      ) : (
        <Grid container spacing={2}>
          {results.map((r) => (
            <Grid item xs={12} md={6} lg={4} key={r.id}>
              <Paper sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <LocalShippingIcon color="primary" />
                  <Typography variant="subtitle1" fontWeight={700}>{r.truckNo}</Typography>
                  {statusLabel[r.status] && (
                    <Chip size="small" sx={{ ml: 'auto' }} label={statusLabel[r.status]} color={statusColor[r.status]} />
                  )}
                </Box>

                {/* Truck details */}
                <Typography variant="body2" color="text.secondary">
                  {r.truckType || 'Truck type not specified'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {r.routeFrom} → {r.routeTo}
                </Typography>
                <Typography variant="body2">Capacity: {r.availableCapacityTon} ton</Typography>

                {r.distanceKm != null && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}>
                    <RouteIcon fontSize="small" color="action" />
                    <Typography variant="body2" color="text.secondary">
                      {r.distanceKm.toFixed(0)} km
                    </Typography>
                  </Box>
                )}

                <Divider sx={{ my: 1 }} />
                <Typography variant="h6">LKR {r.estimatedCost}</Typography>
                <Typography variant="caption" color="text.secondary">Estimated cost</Typography>

                <Button
                  variant="contained" sx={{ mt: 'auto', pt: 2 }} fullWidth
                  disabled={r.status !== 'RECOMMENDED' || acceptingId === r.id}
                  onClick={() => handleAcceptMatch(r.id)}
                >
                  {r.status === 'RECOMMENDED'
                    ? (acceptingId === r.id ? 'Accepting…' : 'Accept Match')
                    : statusLabel[r.status]}
                </Button>
              </Paper>
            </Grid>
          ))}
        </Grid>
      )}
    </Box>
  );
}
