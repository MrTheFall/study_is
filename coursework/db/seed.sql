begin;

-- Roles
insert into roles (id, name, permissions) values
  (1, 'Manager', 'manage_all'),
  (2, 'Cashier', 'take_orders,handle_cash'),
  (3, 'Cook',    'view_queue,update_production')
on conflict do nothing;

-- Employees
insert into employees (id, full_name, login, password_hash, role_id, salary, contact_phone)
values
  (1, 'Eugene Krabs',   'krabs',  '$2a$12$HqFpO5lLdX5ly7cX3BVSw.uHIcXa/QchVDpBhHE45rspEU6GN1VR2', 1, 100000.00, '+1-000-111-2222'),
  (2, 'SpongeBob',      'spongeb', '$2a$12$iHPWDpiJLMa7ZLJRA1ftW.kjsZ.8Q7PuU4cgL4sf6/MYmxcIu8b6O',   3,  40000.00, '+1-000-333-4444'),
  (3, 'Squidward Tentacles', 'squid', '$2a$12$R2V18.LlW4hWbZcBQ4aovuedcm0jEiavnYsYed9t4zkIvtfqYKXoO', 2, 35000.00, '+1-000-555-6666'),
  (10001, 'Admin Manager', 'admin', '$2b$12$TTNHxfAOco6gkzPrewfSIus/zjrWZzf1s59Si/Q6ik86Rife5GLZG', 1, 100000.00, '+1-999-000-0000')
on conflict do nothing;

-- Clients
insert into clients (id, name, phone, email, password_hash, default_address, loyalty_points)
values
  (1, 'Patrick Star', '+1-100-200-3000', 'patrick@example.com', '$2a$12$ujmowcxLII.cASD/ug63PufQbYkyPWsa2c.JMuE/y9Lz86Rm21xVK', 'Rock St. 1', 10),
  (2, 'Sandy Cheeks', '+1-100-200-3001', 'sandy@example.com',   '$2a$12$HGWkQ3lJnd6J3ethqBJW.u.PyOje1ZarhMpjlhPZBf2gos/E6K0MK',   'Dome Ave. 7', 25)
on conflict do nothing;

-- Couriers
insert into couriers (id, name, phone, vehicle_info)
values
  (1, 'Delivery Fish', '+1-200-300-4000', 'Bike'),
  (2, 'Fast Snail',    '+1-200-300-4001', 'Snail-mobile')
on conflict do nothing;

-- Menu items
insert into menu_items (id, name, description, price, available, prep_time_minutes)
values
  (1, 'Krabby Patty', 'Legendary burger', 5.99, true, 7),
  (2, 'Kelp Fries',   'Crispy kelp fries', 2.49, true, 4),
  (3, 'Seafoam Soda', 'Refreshing drink',  1.99, true, 1)
on conflict do nothing;

-- Recipes (1:1 to menu_items)
insert into recipes (id, menu_item_id, name, description, preparation_time, servings, instructions)
values
  (1, 1, 'Krabby Patty Recipe', 'Secret formula', 7, 1, 'Assemble bun, patty, lettuce.'),
  (2, 2, 'Kelp Fries Recipe',   'Fry kelp',       4, 1, 'Cut, fry, salt.'),
  (3, 3, 'Soda Recipe',         'Pour soda',      1, 1, 'Pour into cup.')
on conflict do nothing;

-- Ingredients
insert into ingredients (id, name, unit, cost_per_unit, min_threshold) values
  (1, 'Bun',       'pcs', 0.20,  50),
  (2, 'Patty',     'pcs', 0.80,  30),
  (3, 'Lettuce',   'g',   0.01, 200),
  (4, 'Kelp',      'g',   0.02, 500),
  (5, 'Oil',       'ml',  0.001,1000),
  (6, 'Syrup',     'ml',  0.003,500),
  (7, 'SodaWater', 'ml',  0.001,500)
on conflict do nothing;

-- Ingredient usage per recipe
insert into ingredient_usages (id, recipe_id, ingredient_id, quantity_required, unit_note) values
  (1, 1, 1, 2, 'two buns'),
  (2, 1, 2, 1, 'one patty'),
  (3, 1, 3, 20, '20g lettuce'),
  (4, 2, 4, 150, '150g kelp'),
  (5, 2, 5, 20, '20ml oil'),
  (6, 3, 6, 50, '50ml syrup'),
  (7, 3, 7, 250, '250ml soda')
on conflict do nothing;

-- Inventory records
insert into inventory_records (id, ingredient_id, quantity) values
  (1, 1, 500),
  (2, 2, 400),
  (3, 3, 5000),
  (4, 4, 8000),
  (5, 5, 3000),
  (6, 6, 2000),
  (7, 7, 5000)
on conflict do nothing;

-- Shifts (non-overlapping times per employee per date)
insert into shifts (id, shift_date, start_time, end_time, note) values
  (1, date '2025-01-01', time '09:00', time '17:00', 'Day shift'),
  (2, date '2025-01-01', time '17:00', time '23:00', 'Evening shift')
on conflict do nothing;

-- Employee shifts
insert into employee_shifts (id, employee_id, shift_id, status) values
  (1, 2, 1, 'assigned'), -- SpongeBob on day shift
  (2, 3, 1, 'assigned'), -- Squidward on day shift
  (3, 1, 2, 'assigned')  -- Mr. Krabs on evening shift
on conflict do nothing;

-- Orders
insert into orders (id, client_id, type, status, courier_id, delivered_at, delivery_address)
values
  (1, 1, 'dine_in',  'completed', null, null, null),
  (2, 2, 'delivery', 'delivered', 1, now(), 'Bikini Bottom, Coral St. 123')
on conflict do nothing;

-- Order items (triggers will recalc order totals)
insert into order_items (id, order_id, menu_item_id, quantity, unit_price, note) values
  -- Order 1: 2x Patty, 1x Fries => total 2*5.99 + 2.49 = 14.47
  (1, 1, 1, 2, 5.99, 'extra pickles'),
  (2, 1, 2, 1, 2.49, null),
  -- Order 2: 1x Patty, 2x Soda => total 5.99 + 2*1.99 = 9.97
  (3, 2, 1, 1, 5.99, null),
  (4, 2, 3, 2, 1.99, 'no ice')
on conflict do nothing;

-- Payments (one per order)
insert into payments (id, order_id, method, amount, paid_at, success) values
  (1, 1, 'cash',  14.47, now(), true),
  (2, 2, 'card',   9.97, now(), true)
on conflict do nothing;

-- Reviews (unique per order+client)
insert into reviews (id, order_id, client_id, rating, comment)
values
  (1, 1, 1, 5, 'Best burger ever!'),
  (2, 2, 2, 4, 'Fast delivery, tasty patty')
on conflict do nothing;

-- Sync sequences to the highest seeded ids to avoid collisions on insert
do $$
declare
    max_id integer;
begin
    select coalesce(max(id), 0) into max_id from clients;
    perform setval('clients_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from roles;
    perform setval('roles_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from employees;
    perform setval('employees_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from shifts;
    perform setval('shifts_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from employee_shifts;
    perform setval('employee_shifts_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from couriers;
    perform setval('couriers_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from menu_items;
    perform setval('menu_items_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from recipes;
    perform setval('recipes_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from ingredients;
    perform setval('ingredients_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from ingredient_usages;
    perform setval('ingredient_usages_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from inventory_records;
    perform setval('inventory_records_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from orders;
    perform setval('orders_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from order_items;
    perform setval('order_items_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from payments;
    perform setval('payments_id_seq', max_id + 1, false);

    select coalesce(max(id), 0) into max_id from reviews;
    perform setval('reviews_id_seq', max_id + 1, false);
end $$;

commit;
