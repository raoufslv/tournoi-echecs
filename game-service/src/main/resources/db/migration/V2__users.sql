create table users (
    id bigint generated always as identity primary key,
    email varchar(254) not null unique,
    password_hash varchar(100) not null,
    display_name varchar(50) not null,
    created_at timestamp with time zone not null default now()
);

alter table games add column white_user_id bigint references users (id);
alter table games add column black_user_id bigint references users (id);
