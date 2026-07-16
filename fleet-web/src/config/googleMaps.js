// Central place to read the Google Maps key so it's never hard-coded
// in a component. Get a key from https://console.cloud.google.com/
// (enable "Maps JavaScript API" + "Directions API"), then put it in
// fleet-web/.env as REACT_APP_GOOGLE_MAPS_API_KEY=xxxx
export const GOOGLE_MAPS_API_KEY = process.env.REACT_APP_GOOGLE_MAPS_API_KEY;

export const DEFAULT_MAP_CENTER = { lat: 7.2906, lng: 80.6337 }; // Kandy, Sri Lanka
export const DEFAULT_MAP_ZOOM = 8;
