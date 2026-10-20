import { errorMessage } from './error-messages';

describe('errorMessage', () => {
  it('translates known API codes', () => {
    expect(errorMessage({ status: 403, error: { code: 'NOT_YOUR_TURN' } })).toBe(
      "Ce n'est pas votre tour.",
    );
    expect(errorMessage('GAME_FULL')).toBe('Cette partie est déjà complète.');
  });

  it('falls back for unknown codes', () => {
    expect(errorMessage({ status: 500, error: { code: 'WHATEVER' } })).toContain('erreur');
  });

  it('explains when the server is unreachable', () => {
    expect(errorMessage({ status: 0 })).toContain('injoignable');
  });
});
