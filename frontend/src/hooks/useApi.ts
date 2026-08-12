import React from "react";

import { ApiError } from "@/api/errors";

/**
 * The smallest thing that covers what the screens need: load once, expose
 * loading / error / data, and allow a manual reload.
 *
 * No caching library on purpose — the app has none of the dependencies for one,
 * and the screens here each read a single resource. Polling for `pending`
 * analysis results is handled by `intervalMs`, which the spec requires for
 * result screens rather than showing an empty score slot.
 */
export interface ApiState<T> {
  data: T | null;
  error: ApiError | null;
  loading: boolean;
  reload: () => void;
}

export function useApi<T>(
  fetcher: () => Promise<T>,
  deps: React.DependencyList = [],
  options: { enabled?: boolean; intervalMs?: number } = {},
): ApiState<T> {
  const enabled = options.enabled ?? true;
  const [data, setData] = React.useState<T | null>(null);
  const [error, setError] = React.useState<ApiError | null>(null);
  const [loading, setLoading] = React.useState(enabled);
  const [nonce, setNonce] = React.useState(0);

  // Kept in a ref so a fetcher defined inline does not restart the effect on
  // every render; `deps` is what decides when to refetch.
  const fetcherRef = React.useRef(fetcher);
  fetcherRef.current = fetcher;

  React.useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    let cancelled = false;

    const run = async () => {
      try {
        const result = await fetcherRef.current();
        if (cancelled) return;
        setData(result);
        setError(null);
      } catch (cause) {
        if (cancelled) return;
        setError(
          cause instanceof ApiError
            ? cause
            : new ApiError(0, cause instanceof Error ? cause.message : "실패"),
        );
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    setLoading(true);
    void run();

    if (!options.intervalMs) {
      return () => {
        cancelled = true;
      };
    }
    const timer = setInterval(run, options.intervalMs);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, enabled, nonce, options.intervalMs]);

  const reload = React.useCallback(() => setNonce((n) => n + 1), []);

  return { data, error, loading, reload };
}
