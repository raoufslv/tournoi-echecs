create table outbox_events (
    id           bigint generated always as identity primary key,
    game_id      bigint       not null references games (id),
    event_type   varchar(30)  not null,
    payload      text         not null,
    move_number  integer,
    created_at   timestamptz  not null default now(),
    published_at timestamptz
);

-- Sert au publisher : retrouver vite les lignes pas encore acquittées par Kafka.
create index outbox_events_unpublished_idx on outbox_events (id) where published_at is null;
