import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { UpdateEntryPayload } from '../api/types';
import { renderWithProviders } from '../test/render';
import { makeEntry, server } from '../test/server';
import ItemPage from './ItemPage';

function renderItem() {
  return renderWithProviders(
    <Routes>
      <Route path="/item/:id" element={<ItemPage />} />
    </Routes>,
    { route: '/item/1' },
  );
}

describe('ItemPage', () => {
  it('confirms a save, even though saving remounts the form', async () => {
    let stored = makeEntry();
    server.use(
      http.get('/api/entries/1', () => HttpResponse.json(stored)),
      http.put('/api/entries/1', async ({ request }) => {
        const payload = (await request.json()) as UpdateEntryPayload;
        stored = { ...stored, ...payload, updatedAt: '2026-10-04T12:00:00Z' };
        return HttpResponse.json(stored);
      }),
    );
    renderItem();

    await userEvent.click(await screen.findByRole('radio', { name: '8 out of 10' }));
    expect(screen.getByText('Unsaved changes')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Saved.')).toBeInTheDocument();
  });

  it('says a title is missing only when it is', async () => {
    server.use(
      http.get('/api/entries/1', () =>
        HttpResponse.json({ error: 'not_found', message: 'No such entry' }, { status: 404 }),
      ),
    );
    renderItem();

    expect(await screen.findByRole('alert')).toHaveTextContent('isn’t in your library');
  });

  it('reports a server failure as a failure, not a missing title', async () => {
    server.use(
      http.get('/api/entries/1', () =>
        HttpResponse.json(
          { error: 'internal_error', message: 'Something went wrong. Check the server logs.' },
          { status: 500 },
        ),
      ),
    );
    renderItem();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Couldn’t load that title');
    expect(alert).not.toHaveTextContent('isn’t in your library');
  });

  describe('the finish date', () => {
    // 20:30 UTC: late evening in London, already the next day further east.
    const finishedAt = '2026-09-02T20:30:00Z';
    const sent: UpdateEntryPayload[] = [];

    function stubEntry() {
      sent.length = 0;
      let stored = makeEntry({ status: 'COMPLETED', finishedAt });
      server.use(
        http.get('/api/entries/1', () => HttpResponse.json(stored)),
        http.put('/api/entries/1', async ({ request }) => {
          const payload = (await request.json()) as UpdateEntryPayload;
          sent.push(payload);
          stored = { ...stored, ...payload, updatedAt: '2026-10-04T12:00:00Z' };
          return HttpResponse.json(stored);
        }),
      );
    }

    it('shows the day it fell on in this time zone', async () => {
      stubEntry();
      renderItem();

      const local = new Date(finishedAt);
      const day = `${local.getFullYear()}-${String(local.getMonth() + 1).padStart(2, '0')}-${String(local.getDate()).padStart(2, '0')}`;
      expect(await screen.findByLabelText('Finished')).toHaveValue(day);
    });

    it('keeps the exact moment when the day is left alone', async () => {
      stubEntry();
      renderItem();

      await userEvent.click(await screen.findByRole('radio', { name: '8 out of 10' }));
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

      await waitFor(() => expect(sent).toHaveLength(1));
      expect(sent[0].finishedAt).toBe(finishedAt);
    });

    it('stores a newly picked day as midday there', async () => {
      stubEntry();
      renderItem();

      fireEvent.change(await screen.findByLabelText('Finished'), { target: { value: '2026-09-10' } });
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

      await waitFor(() => expect(sent).toHaveLength(1));
      expect(sent[0].finishedAt).toBe(new Date(2026, 8, 10, 12).toISOString());
    });
  });
});
