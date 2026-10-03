import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { entriesRequests, server } from './server';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));

afterEach(() => {
  server.resetHandlers();
  entriesRequests.length = 0;
});

afterAll(() => server.close());
