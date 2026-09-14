import React, { useEffect, useState, useCallback } from "react";
import { GoogleMap, Marker, useJsApiLoader } from "@react-google-maps/api";
import { Paper, Box, Typography } from "@mui/material";
import { GOOGLE_MAPS_API_KEY, DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM } from "../config/googleMaps";
import axiosClient from "../api/axiosClient";

const containerStyle = { width: "100%", height: "500px" };

export default function RouteGPS() {
  const { isLoaded } = useJsApiLoader({
    id: "google-map-script",
    googleMapsApiKey: GOOGLE_MAPS_API_KEY,
  });

  const [trucks, setTrucks] = useState([]);

  const loadPositions = useCallback(() => {
    axiosClient.get("/gps")
      .then((res) => setTrucks(res.data || []))
      .catch(() => {
        // gps-service not running yet — fine during early development
      });
  }, []);

  useEffect(() => {
    loadPositions();
    const interval = setInterval(loadPositions, 15000); // poll every 15s
    return () => clearInterval(interval);
  }, [loadPositions]);

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>Live GPS Tracking</Typography>
      {isLoaded ? (
        <GoogleMap mapContainerStyle={containerStyle} center={DEFAULT_MAP_CENTER} zoom={DEFAULT_MAP_ZOOM}>
          {trucks.map((truck) => (
            <Marker
              key={truck.truckId}
              position={{ lat: truck.latitude, lng: truck.longitude }}
              label={truck.truckNo}
            />
          ))}
        </GoogleMap>
      ) : (
        <Box sx={{ height: 500, display: "flex", alignItems: "center", justifyContent: "center" }}>
          Loading map…
        </Box>
      )}
    </Paper>
  );
}
