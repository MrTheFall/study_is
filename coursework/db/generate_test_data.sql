begin;

-- Parameters
\set clients 10000
\set menu_items 10000
\set couriers 100
\set orders 50000

-- Clients
insert into clients(name, phone, email, password_hash, default_address, registered_at, loyalty_points)
select 
  'Client '||g,
  '+1-555-'||lpad(g::text, 7, '0'),
  'client'||g||'@example.com',
  'hash'||g,
  'Street '||g,
  (now() - (random()*'365 days'::interval))::date,
  (random()*500)::int
from generate_series(1, :clients) as g;

-- Couriers
insert into couriers(name, phone, vehicle_info)
select 'Courier '||g, '+1-600-'||lpad(g::text, 7, '0'), 'Bike'
from generate_series(1, :couriers) g;

-- Menu items
insert into menu_items(name, description, price, available, prep_time_minutes)
select 
  (case when random() < 0.35 then 'Krabby Patty ' else 'Menu Item ' end) || g,
  'Description '||g,
  round((1 + random()*24)::numeric, 2),
  (random() < 0.9),
  (random()*15)::int
from generate_series(1, :menu_items) g;

-- Orders
with base as (
  select 
    (1 + floor(random()*:clients))::int as client_id,
    random() as r1,
    random() as r2,
    now() - (random()*'180 days'::interval) as created_at,
    g as gid
  from generate_series(1, :orders) g
)
insert into orders(client_id, type, status, created_at, updated_at, total_amount, courier_id, delivered_at, delivery_address)
select 
  b.client_id,
  case when b.r1 < 0.5 then 'dine_in' when b.r1 < 0.8 then 'takeout' else 'delivery' end as type,
  (array['pending','confirmed','preparing','ready','delivering','delivered','completed','cancelled'])[1 + (floor(random()*8))::int] as status,
  b.created_at,
  b.created_at,
  0,
  case when (case when b.r1 < 0.5 then 'dine_in' when b.r1 < 0.8 then 'takeout' else 'delivery' end) = 'delivery' and b.r2 < 0.6 then (1 + floor(random()*:couriers))::int end,
  null,
  case when (case when b.r1 < 0.5 then 'dine_in' when b.r1 < 0.8 then 'takeout' else 'delivery' end) = 'delivery' then 'Address '||b.gid end
from base b;

-- Order items (1-3 items per order)
insert into order_items(order_id, menu_item_id, quantity, unit_price, note)
select o.id,
       (((o.id * 37 + g)::int % :menu_items) + 1) as menu_item_id,
       (1 + ((o.id + g) % 3)) as quantity,
       mi.price,
       null
from orders o
join lateral generate_series(1, (1 + (o.id % 3))) g on true
join menu_items mi on mi.id = (((o.id * 37 + g)::int % :menu_items) + 1);

-- Set delivered_at for delivered/completed (random within few days after created_at)
update orders o
   set delivered_at = o.created_at + (random()*'3 days'::interval)
 where lower(o.status) in ('delivered','completed');

-- Ensure totals (trigger also keeps them, but batch update for completeness)
update orders o
   set total_amount = coalesce((
        select sum(oi.unit_price * oi.quantity) from order_items oi where oi.order_id = o.id
   ),0);

-- Payments (one per order), majority successful
insert into payments(order_id, method, amount, paid_at, success)
select o.id,
       (array['cash','card','online'])[1 + ((o.id % 3))] as method,
       o.total_amount,
       o.created_at + ((o.id % 5)) * interval '1 hour',
       (o.id % 20 <> 0) -- ~95% success
  from orders o
 where o.total_amount > 0;

analyze;
commit;
