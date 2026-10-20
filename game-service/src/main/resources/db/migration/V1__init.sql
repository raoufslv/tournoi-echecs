create table games (
    id bigint generated always as identity primary key,
    code varchar(12) not null unique,
    status varchar(20) not null,
    current_fen varchar(120) not null,
    next_move_number integer not null default 1,
    constraint games_status_check check (status in ('WAITING', 'IN_PROGRESS', 'FINISHED'))
);

create table moves (
    id bigint generated always as identity primary key,
    game_id bigint not null references games (id),
    move_number integer not null,
    uci varchar(5) not null,
    fen_after varchar(120) not null,
    constraint moves_game_number_unique unique (game_id, move_number)
);