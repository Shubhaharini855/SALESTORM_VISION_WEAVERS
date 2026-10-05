const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

const messagesByStatus = {
  400: 'Please check the submitted information.',
  404: 'This item could not be found. Refresh and try again.',
  409: 'Inventory is no longer available or this request was already processed.',
  503: 'The service is temporarily busy. Your request may be safely retried.',
};

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'SALESTORM API is unavailable. Check the backend connection and retry.');
  }

  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json')
    ? await response.json().catch(() => null)
    : null;

  if (!response.ok) {
    const message = messagesByStatus[response.status]
      || (response.status >= 500 ? 'Something went wrong. Please retry.' : 'The request could not be completed.');
    throw new ApiError(response.status, payload?.error || 'REQUEST_FAILED', message);
  }
  return payload;
}

export const api = {
  getProducts: () => request('/api/products'),
  getInventory: (productId) => request(`/api/products/${productId}/inventory`),
  getReservation: (reservationId) => request(`/api/reservations/${reservationId}`),
  createReservation: (body, idempotencyKey, signal) => request('/api/reservations', {
    method: 'POST',
    signal,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(body),
  }),
  checkout: (reservationId, mode, idempotencyKey, simulateOrderServiceFailure = false) => request(`/api/checkout/${reservationId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ mode, simulateOrderServiceFailure }),
  }),
  getDemoSummary: (productId) => request(`/api/demo/summary?productId=${productId}`),
  expireReservation: (reservationId) => request(`/api/demo/reservations/${reservationId}/expire`, { method: 'POST' }),
  resetDemo: () => request('/api/demo/reset', { method: 'POST' }),
  recoverOrder: (orderId) => request(`/api/orders/${orderId}/recover`, { method: 'POST' }),
  health: () => request('/actuator/health'),
};

export function newIdempotencyKey(prefix) {
  const random = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${random}`;
}