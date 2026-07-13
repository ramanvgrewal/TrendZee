import { createFileRoute } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { TrendCard } from '@/components/TrendCard';
import { getArchivedTrends } from '@/lib/archiveApi';
import type { Trend } from '@/lib/mock-data';
import { AlertCircle } from 'lucide-react';

export const Route = createFileRoute('/archive')({
  component: ArchivePage,
});

function ArchivePage() {
  const [archivedTrends, setArchivedTrends] = useState<{ id: string; trendSnapshot: Trend }[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchArchive = async () => {
    const token = localStorage.getItem("token");
    if (!token) {
      setError('Please login to view your archived trends.');
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const data = await getArchivedTrends();
      const mappedData = data.map((item: any) => ({
        ...item,
        trendSnapshot: {
          ...item.trendSnapshot,
          name: item.trendSnapshot.trendName || item.trendSnapshot.name,
          products: item.trendSnapshot.signalProducts,
          supportingSignals: item.trendSnapshot.supportingSignalIds || [],
          signalProducts: item.trendSnapshot.signalProducts ? [item.trendSnapshot.signalProducts] : [],
        }
      }));
      setArchivedTrends(mappedData);
    } catch (err: any) {
      setError(err.message || 'Failed to load archived trends.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchArchive();
  }, []);

  const handleUnarchive = () => {
    fetchArchive(); // Refresh list after unarchiving
  };

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
      {/* Warning Banner */}
      <div className="mb-8 rounded-xl border border-amber-500/20 bg-amber-500/10 p-4 text-amber-600/90 flex items-start gap-3">
        <AlertCircle className="h-5 w-5 shrink-0 mt-0.5" />
        <div className="text-sm font-medium leading-relaxed">
          Warning: Unarchiving a trend will permanently remove it from here. If it is no longer in the main feed, it will be gone forever.
        </div>
      </div>

      <div className="mb-10 flex items-center justify-between">
        <h1 className="font-display text-4xl font-bold tracking-tight text-foreground">Your Archive</h1>
        <span className="rounded-full bg-foreground/[0.03] px-3 py-1 font-mono text-xs uppercase tracking-widest text-foreground/50">
          {archivedTrends.length} saved
        </span>
      </div>

      {loading ? (
        <div className="flex justify-center py-20">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-foreground/20 border-t-foreground"></div>
        </div>
      ) : error ? (
        <div className="rounded-xl border border-red-500/20 bg-red-500/10 p-4 text-center text-red-500">
          {error}
          <div className="mt-4">
            <button onClick={fetchArchive} className="text-sm underline hover:no-underline">Try Again</button>
          </div>
        </div>
      ) : archivedTrends.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-3xl border border-dashed border-foreground/10 py-32 text-center">
          <div className="text-foreground/40 font-mono text-sm uppercase tracking-widest mb-4">Empty Archive</div>
          <p className="text-foreground/60 max-w-md">You haven't archived any trends yet. Click the bookmark icon on any trend to save it here for later.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-10">
          {archivedTrends.map((item) => (
            <TrendCard 
              key={item.id} 
              trend={item.trendSnapshot} 
              isArchivedContext={true} 
              onUnarchive={handleUnarchive}
            />
          ))}
        </div>
      )}
    </div>
  );
}
