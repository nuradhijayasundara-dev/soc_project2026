import apiClient from './apiClient';

// Talks to matching-service through the gateway route /api/matching/**
export const requestBackhaulMatch = (shipmentId) =>
  apiClient.post('/matching/requests', { shipmentId }).then((r) => r.data);

export const getMatchRequest = (requestId) =>
  apiClient.get(`/matching/requests/${requestId}`).then((r) => r.data);

export const getMatchResults = (requestId) =>
  apiClient.get(`/matching/requests/${requestId}/results`).then((r) => r.data);

export const selectMatchResult = (resultId) =>
  apiClient.post(`/matching/results/${resultId}/select`).then((r) => r.data);
