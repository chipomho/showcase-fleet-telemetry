create table if not exists alert (
    id         bigint generated always as identity primary key,
    truck_id   varchar(16)  not null,
    type       varchar(16)  not null,
    severity   varchar(8)   not null,
    message    varchar(200) not null,
    raised_at  timestamptz  not null
);

create index if not exists alert_newest on alert (raised_at desc, id desc);
create index if not exists alert_truck_newest on alert (truck_id, raised_at desc, id desc);
