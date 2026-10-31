alter table tournaments add column minutes integer not null default 10;
alter table tournaments add constraint tournaments_minutes_check check (minutes in (5, 10, 15));

alter table games add column initial_ms integer;
alter table games add column white_remaining_ms bigint;
alter table games add column black_remaining_ms bigint;
alter table games add column clock_deadline timestamptz;
alter table games add column draw_offered_by bigint references users (id);
alter table games add column rematch_game_id bigint references games (id);

create index games_clock_deadline_idx on games (clock_deadline);
