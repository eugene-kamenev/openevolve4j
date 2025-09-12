export function formatScore(fitness, metrics = {}) {
  if (!fitness) return '-';
  if (fitness.error) return 'Error';
  const metricValues = Object.keys(metrics).map(key => fitness[key]);
  return metricValues.map(v => (typeof v === 'number' ? v.toFixed(3) : v)).join(', ');
}
