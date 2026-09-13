#!/usr/bin/env bash
# Downloads and preprocesses the Sri Lanka OpenStreetMap extract for OSRM.
# Runs at image BUILD time (so the container starts instantly) — set the
# OSRM_PBF_URL build arg to rebuild with a different region.
set -euo pipefail

cd /data

echo "[osrm] downloading ${OSRM_PBF_URL} ..."
curl -fSL --retry 3 -o sri-lanka.osm.pbf "$OSRM_PBF_URL"

echo "[osrm] osrm-extract (car profile) ..."
osrm-extract -p /opt/car.lua sri-lanka.osm.pbf

echo "[osrm] osrm-partition (MLD) ..."
osrm-partition sri-lanka.osrm

echo "[osrm] osrm-customize (MLD) ..."
osrm-customize sri-lanka.osrm

# Keep the final image lean — only the prepared .osrm dataset is needed at runtime.
rm -f sri-lanka.osm.pbf

echo "[osrm] Sri Lanka routing dataset ready in /data"