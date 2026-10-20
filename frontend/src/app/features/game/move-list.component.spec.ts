import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MoveEntry } from '../../core/api.models';
import { MoveListComponent } from './move-list.component';

const MOVES: MoveEntry[] = [
  { moveNumber: 1, uci: 'e2e4', san: 'e4', fenAfter: 'f1' },
  { moveNumber: 2, uci: 'e7e5', san: 'e5', fenAfter: 'f2' },
  { moveNumber: 3, uci: 'g1f3', san: 'Nf3', fenAfter: 'f3' },
];

describe('MoveListComponent', () => {
  let fixture: ComponentFixture<MoveListComponent>;

  function setup(moves: MoveEntry[], current = 0): void {
    fixture = TestBed.createComponent(MoveListComponent);
    fixture.componentRef.setInput('moves', moves);
    fixture.componentRef.setInput('current', current);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const buttons = () => Array.from(el().querySelectorAll<HTMLButtonElement>('button.mv'));

  beforeEach(() => TestBed.configureTestingModule({ imports: [MoveListComponent] }));

  it('shows a message when there are no moves', () => {
    setup([]);
    expect(el().textContent).toContain('Aucun coup');
  });

  it('groups moves by pairs under their move number', () => {
    setup(MOVES);
    const rows = el().querySelectorAll('li');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('1.');
    expect(rows[0].textContent).toContain('e4');
    expect(rows[0].textContent).toContain('e5');
    expect(rows[1].textContent).toContain('2.');
    expect(rows[1].textContent).toContain('Nf3');
    expect(buttons().length).toBe(3);
  });

  it('falls back to UCI when a move has no SAN', () => {
    setup([{ moveNumber: 1, uci: 'e2e4', san: null, fenAfter: 'f1' }]);
    expect(buttons()[0].textContent?.trim()).toBe('e2e4');
  });

  it('highlights the move currently shown on the board', () => {
    setup(MOVES, 2);
    const current = buttons().filter((b) => b.classList.contains('current'));
    expect(current.length).toBe(1);
    expect(current[0].textContent?.trim()).toBe('e5');
  });

  it('highlights nothing at the start position', () => {
    setup(MOVES, 0);
    expect(buttons().some((b) => b.classList.contains('current'))).toBeFalse();
  });

  it('emits the move number when a move is clicked', () => {
    setup(MOVES);
    const selected: number[] = [];
    fixture.componentInstance.selected.subscribe((n) => selected.push(n));

    buttons()[1].click();

    expect(selected).toEqual([2]);
  });
});
