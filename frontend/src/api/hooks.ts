import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from '@tanstack/react-query';
import { api } from './client';
import type {
  CreateEntryPayload,
  Entry,
  EntryFilters,
  EntryStatus,
  MediaType,
  UpdateEntryPayload,
} from './types';

export const queryKeys = {
  me: ['me'] as const,
  entries: (filters: EntryFilters) => ['entries', filters] as const,
  entry: (id: number) => ['entry', id] as const,
  stats: ['stats'] as const,
  search: (query: string, type: MediaType) => ['search', type, query] as const,
  recommendations: (type: MediaType) => ['recommendations', type] as const,
};

export function useEntries(filters: EntryFilters) {
  return useQuery({
    queryKey: queryKeys.entries(filters),
    queryFn: () => api.listEntries(filters),
    placeholderData: (previous) => previous,
  });
}

export function useEntry(id: number) {
  return useQuery({
    queryKey: queryKeys.entry(id),
    queryFn: () => api.getEntry(id),
    enabled: Number.isFinite(id),
  });
}

export function useStats() {
  return useQuery({ queryKey: queryKeys.stats, queryFn: api.stats });
}

export function useSearch(query: string, type: MediaType) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.search(trimmed, type),
    queryFn: () => api.search(trimmed, type),
    enabled: trimmed.length >= 2,
    // Provider results don't change minute to minute, and this keeps us well
    // clear of TMDB's and IGDB's rate limits while someone retypes a title.
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

export function useRecommendations(type: MediaType) {
  return useQuery({
    queryKey: queryKeys.recommendations(type),
    queryFn: () => api.recommendations(type),
    // The server caches these per user; a cold build costs one provider call
    // per seed, so don't refetch on every mount.
    staleTime: 10 * 60 * 1000,
    retry: false,
  });
}

/** Anything that changes an entry invalidates the library, that entry, and the stats. */
function useEntryMutation<TArgs>(
  mutationFn: (args: TArgs) => Promise<Entry | void>,
): UseMutationResult<Entry | void, Error, TArgs> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (entry) => {
      void queryClient.invalidateQueries({ queryKey: ['entries'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.stats });
      void queryClient.invalidateQueries({ queryKey: ['search'] });
      // Adding or scoring something changes what gets suggested next.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      if (entry) {
        queryClient.setQueryData(queryKeys.entry(entry.id), entry);
      }
    },
  });
}

export function useCreateEntry() {
  return useEntryMutation((payload: CreateEntryPayload) => api.createEntry(payload));
}

export function useUpdateEntry(id: number) {
  return useEntryMutation((payload: UpdateEntryPayload) => api.updateEntry(id, payload));
}

export function useUpdateStatus() {
  return useEntryMutation(({ id, status }: { id: number; status: EntryStatus }) =>
    api.updateEntryStatus(id, status),
  );
}

export function useDeleteEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteEntry(id),
    onSuccess: (_result, id) => {
      queryClient.removeQueries({ queryKey: queryKeys.entry(id) });
      void queryClient.invalidateQueries({ queryKey: ['entries'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.stats });
      void queryClient.invalidateQueries({ queryKey: ['search'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
  });
}
