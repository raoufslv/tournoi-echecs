import { OPPONENT_MOVED_TITLE, PAGE_TITLE, shouldNotifyOpponentMove, titleAfterMove } from './notify';

describe('shouldNotifyOpponentMove', () => {
  it('notifies when the opponent moves in a hidden tab', () => {
    expect(shouldNotifyOpponentMove(true, 'black', 'white')).toBeTrue();
  });

  it('stays quiet for your own move, a visible tab, or a spectator', () => {
    expect(shouldNotifyOpponentMove(true, 'white', 'white')).toBeFalse();
    expect(shouldNotifyOpponentMove(false, 'black', 'white')).toBeFalse();
    expect(shouldNotifyOpponentMove(true, 'black', null)).toBeFalse();
  });

  it('switches the tab title only when a notification is due', () => {
    expect(titleAfterMove(true)).toBe(OPPONENT_MOVED_TITLE);
    expect(titleAfterMove(false)).toBe(PAGE_TITLE);
  });
});
