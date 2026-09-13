# Local OSRM routing engine, pre-built with the Sri Lanka road network.
#
# The Geofabrik extract is downloaded and preprocessed (osrm-extract -> 
# osrm-partition -> osrm-customize, MLD algorithm) ONCE at image build time, so
# the running container starts instantly and offline. Swap OSRM_PBF_URL for any
# other Geofabrik region later without changing anything else.
FROM ghcr.io/project-osrm/osrm-backend:latest

USER root

# curl is used only for the build-time download + the compose healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV OSRM_PBF_URL=https://download.geofabrik.de/asia/sri-lanka-latest.osm.pbf

WORKDIR /data

COPY prepare.sh /usr/local/bin/prepare.sh
RUN chmod +x /usr/local/bin/prepare.sh && /usr/local/bin/prepare.sh

# osrm-routed exposes the HTTP API (Route / Nearest / Table / ...) here.
EXPOSE 5000

CMD ["osrm-routed", "--algorithm", "mld", "/data/sri-lanka.osrm"]