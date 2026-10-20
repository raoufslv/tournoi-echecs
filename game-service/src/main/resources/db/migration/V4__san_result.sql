-- Les coups joues avant cette migration n'ont pas de notation SAN : la colonne reste nullable.
alter table moves add column san varchar(12);

-- Resultat et raison de fin, renseignes quand la partie passe a FINISHED.
alter table games add column result varchar(7);
alter table games add column end_reason varchar(20);
