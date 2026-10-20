import { TestBed } from '@angular/core/testing';

import { LiveMessage, LiveService, EVENT_SOURCE_FACTORY } from './live.service';

/** Faux EventSource pilotable depuis le test. */
class FakeEventSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private readonly listeners = new Map<string, ((m: MessageEvent<string>) => void)[]>();

  constructor(readonly url: string) {}

  addEventListener(type: string, listener: (m: MessageEvent<string>) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  emit(type: string, data: string): void {
    for (const l of this.listeners.get(type) ?? []) {
      l({ data } as MessageEvent<string>);
    }
  }

  close(): void {
    this.closed = true;
  }
}

describe('LiveService', () => {
  let source: FakeEventSource;
  let service: LiveService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: EVENT_SOURCE_FACTORY,
          useValue: (url: string) => (source = new FakeEventSource(url)),
        },
      ],
    });
    service = TestBed.inject(LiveService);
  });

  it('connects to the watch URL of the game', () => {
    service.watch('ABC123').subscribe();
    expect(source.url).toBe('http://localhost:8081/games/ABC123/watch');
  });

  it('emits open, parsed events and errors', () => {
    const received: LiveMessage[] = [];
    service.watch('ABC123').subscribe((m) => received.push(m));

    source.onopen?.();
    source.emit(
      'move.played',
      JSON.stringify({ type: 'move.played', code: 'ABC123', moveNumber: 1, uci: 'e2e4', fenAfter: 'x', playedBy: 'white' }),
    );
    source.onerror?.();

    expect(received.map((m) => m.kind)).toEqual(['open', 'event', 'error']);
    const event = received[1] as Extract<LiveMessage, { kind: 'event' }>;
    expect(event.event.type).toBe('move.played');
  });

  it('ignores unreadable messages', () => {
    const received: LiveMessage[] = [];
    service.watch('ABC123').subscribe((m) => received.push(m));

    source.emit('move.played', 'pas du json');

    expect(received).toEqual([]);
  });

  it('closes the connection on unsubscribe', () => {
    const subscription = service.watch('ABC123').subscribe();
    subscription.unsubscribe();
    expect(source.closed).toBeTrue();
  });
});
