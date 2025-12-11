create table if not exists coordinates (
    id bigserial primary key,
    x integer not null,
    y real not null,
    constraint coordinates_x_min check (x > -524),
    constraint coordinates_y_max check (y <= 476)
);

-- Address
create table if not exists address (
    id bigserial primary key,
    street text not null,
    zip_code text null,
    constraint address_street_not_blank check (char_length(btrim(street)) > 0)
);

-- Organization
-- Ensure old unique and FK constraints are relaxed (idempotent)
alter table if exists organization drop constraint if exists organization_coordinates_id_key;
alter table if exists organization drop constraint if exists organization_official_address_id_key;
alter table if exists organization drop constraint if exists organization_postal_address_id_key;
alter table if exists organization drop constraint if exists fk_org_coordinates;
alter table if exists organization drop constraint if exists fk_org_official_address;
alter table if exists organization drop constraint if exists fk_org_postal_address;

create table if not exists organization (
    id integer generated always as identity primary key,
    name text not null,
    full_name text not null,
    type text null,
    annual_turnover real not null,
    employees_count bigint not null,
    rating double precision not null,
    creation_date timestamp not null default now(),
    coordinates_id bigint not null,
    official_address_id bigint not null,
    postal_address_id bigint not null,
    constraint org_id_positive check (id > 0),
    constraint org_name_not_blank check (char_length(btrim(name)) > 0),
    constraint org_full_name_not_blank check (char_length(btrim(full_name)) > 0),
    constraint org_turnover_pos check (annual_turnover > 0),
    constraint org_employees_pos check (employees_count > 0),
    constraint org_rating_pos check (rating > 0),
    constraint org_type_valid check (type in ('COMMERCIAL','PUBLIC','TRUST','PRIVATE_LIMITED_COMPANY') or type is null)
);

-- Re-add FKs without cascade (restrict deletion of referenced rows)
alter table if exists organization
    add constraint fk_org_coordinates foreign key (coordinates_id) references coordinates(id) on delete restrict;
alter table if exists organization
    add constraint fk_org_official_address foreign key (official_address_id) references address(id) on delete restrict;
alter table if exists organization
    add constraint fk_org_postal_address foreign key (postal_address_id) references address(id) on delete restrict;

-- (Application performs orphan cleanup after updates/deletes)
