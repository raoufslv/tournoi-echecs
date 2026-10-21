import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { Standing, Tournament } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { LiveService, TournamentLiveMessage } from '../../core/live.service';
import { TournamentService } from '../../core/tournament.service';
import { TournamentComponent } from './tournament.component';

function standing(userId: number, name: string, score = 0, rank = 1): Standing {
  return { rank, userId, name, score, buchholz: 0, hadBye: false };
}

function tournament(overrides: Partial<Tournament> = {}): Tournament {
  return {
    code: 'TOURN1',
    name: 'Soirée du vendredi',
    status: 'REGISTRATION',
    roundsTotal: 3,
    currentRound: 0,
    organizerId: 1,
    organizerName: 'Alice',
    standings: [standing(1, 'Alice')],
    rounds: [],
    ...overrides,
  };
}

const FOUR = [standing(1, 'Alice'), standing(2, 'Bob'), standing(3, 'Cleo'), standing(4, 'Dan')];

const RUNNING = tournament({
  status: 'IN_PROGRESS',
  currentRound: 1,
  standings: FOUR,
  rounds: [
    {
      round: 1,
      byeUserId: null,
      byeName: null,
      games: [
        { gameCode: 'MINE11', whiteId: 1, whiteName: 'Alice', blackId: 2, blackName: 'Bob', status: 'IN_PROGRESS', result: null },
        { gameCode: 'OTHER2', whiteId: 3, whiteName: 'Cleo', blackId: 4, blackName: 'Dan', status: 'IN_PROGRESS', result: null },
      ],
    },
  ],
});

async function render(userId: number, initial: Tournament, overrides: Record<string, unknown> = {}) {
  const live = new Subject<TournamentLiveMessage>();
  const get = jasmine.createSpy('get').and.returnValue(of(initial));
  const join = jasmine.createSpy('join').and.returnValue(of(initial));
  const start = jasmine.createSpy('start').and.returnValue(of(initial));
  TestBed.configureTestingModule({
    imports: [TournamentComponent],
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ code: 'TOURN1' }) } } },
      { provide: TournamentService, useValue: { get, join, start, ...overrides } },
      { provide: AuthService, useValue: { userId: () => userId } },
      { provide: LiveService, useValue: { watchTournament: () => live.asObservable() } },
    ],
  });
  const fixture = TestBed.createComponent(TournamentComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const el = () => fixture.nativeElement as HTMLElement;
  const buttonWithText = (text: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes(text),
    );
  return { fixture, live, get, join, start, el, buttonWithText };
}

describe('TournamentComponent', () => {
  it('shows the registration list with the code to share', async () => {
    const { fixture, el } = await render(1, tournament());

    expect(el().querySelector('h1')?.textContent).toContain('Soirée du vendredi');
    expect(el().textContent).toContain('Inscrits');
    expect(el().textContent).toContain('TOURN1');
    expect(el().textContent).toContain('Alice');
    fixture.destroy();
  });

  it('offers to join when the viewer is not registered', async () => {
    const { fixture, buttonWithText, join } = await render(9, tournament());

    buttonWithText('Rejoindre le tournoi')!.click();

    expect(join).toHaveBeenCalledWith('TOURN1');
    fixture.destroy();
  });

  it('does not offer to join to a player who is already registered', async () => {
    const { fixture, buttonWithText } = await render(1, tournament());
    expect(buttonWithText('Rejoindre le tournoi')).toBeUndefined();
    fixture.destroy();
  });

  it('lets only the organizer start, and only with enough players', async () => {
    const few = await render(1, tournament());
    expect(few.buttonWithText('Lancer le tournoi')!.disabled).toBeTrue();
    expect(few.el().textContent).toContain('au moins 4 joueurs');
    few.fixture.destroy();
    TestBed.resetTestingModule();

    const guest = await render(2, tournament({ standings: FOUR }));
    expect(guest.buttonWithText('Lancer le tournoi')).toBeUndefined();
    guest.fixture.destroy();
    TestBed.resetTestingModule();

    const ready = await render(1, tournament({ standings: FOUR }));
    const button = ready.buttonWithText('Lancer le tournoi')!;
    expect(button.disabled).toBeFalse();
    button.click();
    expect(ready.start).toHaveBeenCalledWith('TOURN1');
    ready.fixture.destroy();
  });

  it('shows the standings and the pairings once started', async () => {
    const { fixture, el } = await render(1, RUNNING);

    expect(el().textContent).toContain('Classement');
    expect(el().textContent).toContain('Ronde 1 / 3');
    expect(el().querySelectorAll('.pairings li').length).toBe(2);
    fixture.destroy();
  });

  it('links my own game as "Jouer ma partie" and the others as "Suivre"', async () => {
    const { fixture, el } = await render(1, RUNNING);

    const links = Array.from(el().querySelectorAll<HTMLAnchorElement>('.pairings a'));
    expect(links[0].textContent).toContain('Jouer ma partie');
    expect(links[0].getAttribute('href')).toBe('/game/MINE11');
    expect(links[1].textContent).toContain('Suivre');
    expect(links[1].getAttribute('href')).toBe('/game/OTHER2');
    fixture.destroy();
  });

  it('only lets spectators follow games', async () => {
    const { fixture, el } = await render(99, RUNNING);

    const labels = Array.from(el().querySelectorAll('.pairings a')).map((a) => a.textContent?.trim());
    expect(labels).toEqual(['Suivre', 'Suivre']);
    fixture.destroy();
  });

  it('shows the bye of a round', async () => {
    const withBye = tournament({
      ...RUNNING,
      rounds: [{ ...RUNNING.rounds[0], byeUserId: 5, byeName: 'Eve' }],
    });
    const { fixture, el } = await render(1, withBye);

    expect(el().querySelector('.bye')?.textContent).toContain('Eve');
    fixture.destroy();
  });

  it('announces the winner when the tournament is finished', async () => {
    const finished = tournament({
      ...RUNNING,
      status: 'FINISHED',
      standings: [standing(2, 'Bob', 2.5, 1), standing(1, 'Alice', 1.5, 2)],
    });
    const { fixture, el } = await render(1, finished);

    expect(el().querySelector('.result-banner')?.textContent).toContain('Vainqueur : Bob');
    expect(el().textContent).toContain('2,5');
    fixture.destroy();
  });

  it('reloads the tournament from the server on every live signal', async () => {
    const { fixture, live, get } = await render(1, RUNNING);
    expect(get).toHaveBeenCalledTimes(1);

    live.next({ kind: 'open' });
    live.next({ kind: 'updated' });

    expect(get).toHaveBeenCalledTimes(3);
    fixture.destroy();
  });

  it('shows the new state after an update signal', async () => {
    const { fixture, live, get, el } = await render(1, RUNNING);
    get.and.returnValue(of({ ...RUNNING, status: 'FINISHED' as const }));

    live.next({ kind: 'updated' });
    fixture.detectChanges();

    expect(el().textContent).toContain('Tournoi terminé');
    fixture.destroy();
  });

  it('warns when the live stream is interrupted and does not reload', async () => {
    const { fixture, live, get, el } = await render(1, RUNNING);

    live.next({ kind: 'error' });
    fixture.detectChanges();

    expect(el().textContent).toContain('Direct interrompu');
    expect(get).toHaveBeenCalledTimes(1);
    live.next({ kind: 'open' });
    fixture.detectChanges();
    expect(el().textContent).not.toContain('Direct interrompu');
    fixture.destroy();
  });

  it('shows the error returned when starting fails', async () => {
    const start = jasmine
      .createSpy('start')
      .and.returnValue(throwError(() => ({ status: 409, error: { code: 'NOT_ENOUGH_PLAYERS' } })));
    const { fixture, el, buttonWithText } = await render(1, tournament({ standings: FOUR }), { start });

    buttonWithText('Lancer le tournoi')!.click();
    fixture.detectChanges();

    expect(el().querySelector('.error')?.textContent).toContain('au moins 4 joueurs');
    fixture.destroy();
  });

  it('shows a message when the tournament does not exist', async () => {
    const get = jasmine
      .createSpy('get')
      .and.returnValue(throwError(() => ({ status: 404, error: { code: 'TOURNAMENT_NOT_FOUND' } })));
    const { fixture, el } = await render(1, tournament(), { get });

    expect(el().querySelector('.error')?.textContent).toContain('Tournoi introuvable');
    fixture.destroy();
  });
});
