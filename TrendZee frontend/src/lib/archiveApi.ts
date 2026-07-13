const baseUrl = import.meta.env?.VITE_API_BASE_URL || (typeof process !== 'undefined' && process.env?.VITE_API_BASE_URL) || (import.meta.env?.DEV ? "http://localhost:8080" : "");

const getHeaders = () => {
  const token = localStorage.getItem("token");
  const headers: HeadersInit = {
    'Content-Type': 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

export const archiveTrend = async (trendId: string) => {
  const res = await fetch(`${baseUrl}/api/v2/archive/trends/${trendId}`, {
    method: 'POST',
    headers: getHeaders(),
  });
  if (!res.ok) {
    throw new Error('Failed to archive trend');
  }
  return res.json();
};

export const unarchiveTrend = async (trendId: string) => {
  const res = await fetch(`${baseUrl}/api/v2/archive/trends/${trendId}`, {
    method: 'DELETE',
    headers: getHeaders(),
  });
  if (!res.ok) {
    throw new Error('Failed to unarchive trend');
  }
};

export const getArchivedTrends = async () => {
  const res = await fetch(`${baseUrl}/api/v2/archive/trends`, {
    method: 'GET',
    headers: getHeaders(),
  });
  if (!res.ok) {
    throw new Error('Failed to fetch archived trends');
  }
  return res.json();
};

export const getArchiveStatus = async (trendId: string) => {
  const res = await fetch(`${baseUrl}/api/v2/archive/trends/${trendId}/status`, {
    method: 'GET',
    headers: getHeaders(),
  });
  if (!res.ok) {
    throw new Error('Failed to fetch archive status');
  }
  return res.json();
};
