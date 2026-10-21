create table tournaments (
    id              bigint generated always as identity primary key,
    code            varchar(12)  not null unique,
    name            varchar(60)  not null,
    status          varchar(20)  not null,
    created_by      bigint       not null references users (id),
    rounds_total    integer      not null,
    current_round   integer      not null default 0,
    created_at      timestamptz  not null default now(),
    constraint tournaments_status_check check (status in ('REGISTRATION', 'IN_PROGRESS', 'FINISHED'))
);

-- Le score est stocke en demi-points (victoire = 2, nulle = 1) pour eviter les decimaux.
create table tournament_participants (
    id             bigint generated always as identity primary key,
    tournament_id  bigint       not null references tournaments (id),
    user_id        bigint       not null references users (id),
    half_points    integer      not null default 0,
    bye_round      integer,
    joined_at      timestamptz  not null default now(),
    constraint tournament_participants_unique unique (tournament_id, user_id)
);

alter table games add column tournament_id bigint references tournaments (id);
alter table games add column round integer;
create index games_tournament_idx on games (tournament_id, round);

-- Un evenement concerne soit une partie, soit un tournoi.
alter table outbox_events alter column game_id drop not null;
alter table outbox_events add column tournament_id bigint references tournaments (id);
alter table outbox_events add constraint outbox_events_target_check
    check ((game_id is not null) <> (tournament_id is not null));
