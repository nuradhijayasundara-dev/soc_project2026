import React, { useCallback, useEffect, useState } from 'react';
import { GoogleMap, useJsApiLoader, MarkerF, InfoWindowF } from '@react-google-maps/api';
import { Box, Typography, Paper, Alert, CircularProgress, Chip } from '@mui/material';
import { getLiveLocations } from '../api/gpsApi';
import { getTrucks } from '../api/truckApi';

const containerStyle = { width: '100%', height: '600px', borderRadius: 8 };

// Fallback center (roughly central Sri Lanka, matching the courier/fleet demo data) —
// re-centers on the trucks themselves once locations load.
const DEFAULT_CENTER = { lat: 7.8731, lng: 80.7718 };

const REFRESH_INTERVAL_MS = 10000; // poll every 10s; swap for WebSocket/SSE for true real-time

export default function GpsTracking() {
  const { isLoaded, loadError } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: process.env.REACT_APP_GOOGLE_MAPS_API_KEY || '',
  });

  const [locations, setLocations] = useState([]);
  const [trucksById, setTrucksById] = useState({});
  const [selectedTruckId, setSelectedTruckId] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async () => {
    try {
      const [live, trucks] = await Promise.all([getLiveLocations(), getTrucks()]);
      setLocations(live);
      setTrucksById(Object.fromEntries(trucks.map((t) => [t.id, t])));
      setError('');
    } catch {
      setError('Could not load live GPS data (is gps-service running?)');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, REFRESH_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [loadData]);

  if (!process.env.REACT_APP_GOOGLE_MAPS_API_KEY) {
    return (
      <Alert severity="warning">
        Set <code>REACT_APP_GOOGLE_MAPS_API_KEY</code> in <code>.env.local</code> (see
        <code> .env.example</code>) to enable the live map.
      </Alert>
    );
  }
  if (loadError) return <Alert severity="error">Failed to load Google Maps.</Alert>;
  if (!isLoaded || loading) return <CircularProgress />;

  const center = locations.length
    ? { lat: locations[0].latitude, lng: locations[0].longitude }
    : DEFAULT_CENTER;

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>GPS Tracking</Typography>
      {error && <Alert severity="warning" sx={{ mb: 2 }}>{error}</Alert>}

      <Paper sx={{ p: 1 }}>
        <GoogleMap mapContainerStyle={containerStyle} center={center} zoom={locations.length ? 8 : 7}>
          {locations.map((loc) => {
            const truck = trucksById[loc.truckId];
            return (
              <MarkerF
                key={loc.truckId}
                position={{ lat: loc.latitude, lng: loc.longitude }}
                label={{ text: truck?.truckNo || String(loc.truckId), fontSize: '11px' }}
                onClick={() => setSelectedTruckId(loc.truckId)}
              >
                {selectedTruckId === loc.truckId && (
                  <InfoWindowF onCloseClick={() => setSelectedTruckId(null)}>
                    <Box sx={{ minWidth: 160 }}>
                      <Typography variant="subtitle2" fontWeight={700}>
                        {truck?.truckNo || `Truck #${loc.truckId}`}
                      </Typography>
                      <Chip
                        size="small"
                        sx={{ my: 0.5 }}
                        label={truck?.status || 'UNKNOWN'}
                        color={truck?.status === 'AVAILABLE' ? 'success' : 'info'}
                      />
                      <Typography variant="body2">Speed: {(loc.speedKmh ?? 0).toFixed(0)} km/h</Typography>
                      <Typography variant="caption" color="text.secondary">
                        Updated: {new Date(loc.updatedAt).toLocaleTimeString()}
                      </Typography>
                    </Box>
                  </InfoWindowF>
                )}
              </MarkerF>
            );
          })}
        </GoogleMap>
      </Paper>

      {locations.length === 0 && !error && (
        <Typography color="text.secondary" sx={{ mt: 2 }}>
          No trucks are transmitting location yet — start a trip from the Driver App to see it here.
        </Typography>
      )}
    </Box>
  );
}
