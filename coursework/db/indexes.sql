-- Proposed indexes for critical use-cases (created by benchmark script)

-- 1) Kitchen queue: orders by status then created_at
create index concurrently if not exists idx_orders_status_created_at
    on orders(status, created_at);

-- 2) Client order history: orders by client then created_at desc
create index concurrently if not exists idx_orders_client_created_at
    on orders(client_id, created_at desc);

-- 3) Payments in time window: partial index on successful payments by paid_at
create index concurrently if not exists idx_payments_paid_at_success
    on payments(paid_at) where success = true;

-- 4) Menu search by name: trigram index
create extension if not exists pg_trgm;
create index concurrently if not exists idx_menu_items_name_trgm
    on menu_items using gin (name gin_trgm_ops);

-- 5) Shifts on a date ordered by start_time
create index concurrently if not exists idx_shifts_date_start_time
    on shifts(shift_date, start_time);

