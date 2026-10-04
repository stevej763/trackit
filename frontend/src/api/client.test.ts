import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { api } from './client';

describe('api.stats', () => {
  it('sends the browser time zone, which decides the month each finish falls in', async () => {
    let tz: string | null = null;
    server.use(
      http.get('/api/stats', ({ request }) => {
        tz = new URL(request.url).searchParams.get('tz');
        return HttpResponse.json({});
      }),
    );

    await api.stats();

    expect(tz).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });
});
