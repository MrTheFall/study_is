create or replace function order_required_ingredients(p_order_id integer)
returns table (
    ingredient_id integer,
    required_qty numeric(14,3)
)
language sql
stable
as $$
    select iu.ingredient_id,
           round(sum((iu.quantity_required / r.servings::numeric) * oi.quantity), 3)::numeric(14,3) as required_qty
      from order_items oi
      join recipes r on r.menu_item_id = oi.menu_item_id
      join ingredient_usages iu on iu.recipe_id = r.id
     where oi.order_id = p_order_id
     group by iu.ingredient_id;
$$;

create or replace function assert_ingredients_available_for_order(p_order_id integer)
returns void
language plpgsql as $$
declare
    v_insufficient record;
begin
    select req.ingredient_id,
           i.name as ingredient_name,
           req.required_qty,
           coalesce(ir.quantity, 0)::numeric(14,3) as available_qty
      into v_insufficient
      from order_required_ingredients(p_order_id) req
      join ingredients i on i.id = req.ingredient_id
 left join inventory_records ir on ir.ingredient_id = req.ingredient_id
     where coalesce(ir.quantity, 0) < req.required_qty
     order by i.id
     limit 1;

    if found then
        raise exception 'insufficient ingredients for order %: ingredient % (id=%) required=%, available=%',
            p_order_id,
            v_insufficient.ingredient_name,
            v_insufficient.ingredient_id,
            v_insufficient.required_qty,
            v_insufficient.available_qty;
    end if;
end;$$;

create or replace function consume_ingredients_for_order(p_order_id integer)
returns void
language plpgsql as $$
declare
    v_req record;
    v_available numeric(14,3);
    v_ingredient_name text;
begin
    for v_req in
        select req.ingredient_id, req.required_qty
          from order_required_ingredients(p_order_id) req
         where req.required_qty > 0
         order by req.ingredient_id
    loop
        -- Ensure record exists (unique per ingredient_id), then lock it.
        insert into inventory_records(ingredient_id, quantity)
        values (v_req.ingredient_id, 0)
        on conflict (ingredient_id) do nothing;

        select ir.quantity
          into v_available
          from inventory_records ir
         where ir.ingredient_id = v_req.ingredient_id
         for update;

        if v_available < v_req.required_qty then
            select i.name into v_ingredient_name from ingredients i where i.id = v_req.ingredient_id;
            raise exception 'insufficient ingredients for order %: ingredient % (id=%) required=%, available=%',
                p_order_id,
                coalesce(v_ingredient_name, '<unknown>'),
                v_req.ingredient_id,
                v_req.required_qty,
                v_available;
        end if;

        update inventory_records
           set quantity = quantity - v_req.required_qty,
               last_updated = now()
         where ingredient_id = v_req.ingredient_id;

        if to_regclass('public.inventory_transactions') is not null then
            execute 'insert into inventory_transactions(ingredient_id, delta, reason, source, order_id, created_at)
                     values ($1, $2, $3, $4, $5, now())'
                using v_req.ingredient_id,
                      -v_req.required_qty,
                      'Order confirmed',
                      'order',
                      p_order_id;
        end if;
    end loop;
end;$$;

create or replace function restore_ingredients_for_order(p_order_id integer)
returns void
language plpgsql as $$
declare
    v_req record;
begin
    for v_req in
        select req.ingredient_id, req.required_qty
          from order_required_ingredients(p_order_id) req
         where req.required_qty > 0
         order by req.ingredient_id
    loop
        insert into inventory_records(ingredient_id, quantity, last_updated)
        values (v_req.ingredient_id, v_req.required_qty, now())
        on conflict (ingredient_id) do update
            set quantity = inventory_records.quantity + excluded.quantity,
                last_updated = now();

        if to_regclass('public.inventory_transactions') is not null then
            execute 'insert into inventory_transactions(ingredient_id, delta, reason, source, order_id, created_at)
                     values ($1, $2, $3, $4, $5, now())'
                using v_req.ingredient_id,
                      v_req.required_qty,
                      'Order cancelled',
                      'order',
                      p_order_id;
        end if;
    end loop;
end;$$;

create unique index if not exists uq_inventory_records_ingredient_id on inventory_records(ingredient_id);

create or replace function place_order(
    p_client_id integer,
    p_type text,
    p_delivery_address text,
    p_payment_method text,
    p_items jsonb
) returns integer
language plpgsql as $$
declare
    v_order_id integer;
    v_elem jsonb;
    v_menu_id integer;
    v_qty integer;
    v_price numeric(14,2);
    v_menu_available boolean;
begin
    if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
        raise exception 'place_order: items must be a non-empty JSON array';
    end if;

    if lower(p_type) = 'delivery' and (p_delivery_address is null or length(trim(p_delivery_address)) = 0) then
        raise exception 'place_order: delivery requires delivery_address';
    end if;

    if p_payment_method is not null and lower(p_payment_method) not in ('cash','card','online') then
        raise exception 'place_order: invalid payment_method %', p_payment_method;
    end if;

    if lower(p_type) = 'delivery' and p_payment_method is null then
        raise exception 'place_order: delivery requires payment_method';
    end if;

    insert into orders (client_id, type, status, delivery_address, payment_method)
    values (p_client_id, lower(p_type), 'pending', p_delivery_address, lower(p_payment_method))
    returning id into v_order_id;

    for v_elem in select * from jsonb_array_elements(p_items)
    loop
        v_menu_id := (v_elem->>'menu_item_id')::int;
        v_qty     := coalesce((v_elem->>'quantity')::int, 0);
        v_price   := nullif((v_elem->>'unit_price')::numeric, null);
        if v_menu_id is null or v_qty is null or v_qty <= 0 then
            raise exception 'place_order: invalid item payload: %', v_elem::text;
        end if;

        select mi.available,
               coalesce(v_price, mi.price)
          into v_menu_available, v_price
          from menu_items mi
         where mi.id = v_menu_id;

        if not found then
            raise exception 'place_order: menu_item % not found', v_menu_id;
        end if;
        if not v_menu_available then
            raise exception 'place_order: menu_item % is not available', v_menu_id;
        end if;
        if v_price < 0 then
            raise exception 'place_order: negative price for item %', v_menu_id;
        end if;

        insert into order_items(order_id, menu_item_id, quantity, unit_price)
        values (v_order_id, v_menu_id, v_qty, v_price);
    end loop;

    if not exists (select 1 from order_items where order_id = v_order_id) then
        raise exception 'place_order: no items created for order %', v_order_id;
    end if;

    perform assert_ingredients_available_for_order(v_order_id);

    return v_order_id;
end;$$;


create or replace function update_order_status(
    p_order_id integer,
    p_new_status text
) returns void
language plpgsql as $$
declare
    v_cur text;
    v_type text;
    v_courier_id integer;
    v_allowed boolean := false;
begin
    select status, type, courier_id into v_cur, v_type, v_courier_id from orders where id = p_order_id;
    if not found then
        raise exception 'update_order_status: order % not found', p_order_id;
    end if;

    p_new_status := lower(p_new_status);
    v_cur := lower(v_cur);

    if v_cur = p_new_status then
        return;
    end if;
    v_allowed :=
        (v_cur = 'pending'   and p_new_status in ('confirmed','cancelled')) or
        (v_cur = 'confirmed' and p_new_status in ('preparing','cancelled')) or
        (v_cur = 'preparing' and p_new_status in ('ready','cancelled')) or
        (v_cur = 'ready'     and p_new_status in ('delivering','completed','cancelled')) or
        (v_cur = 'delivering' and p_new_status in ('delivered','cancelled')) or
        (v_cur = 'delivered' and p_new_status in ('completed'));

    if not v_allowed then
        raise exception 'update_order_status: transition % -> % is not allowed', v_cur, p_new_status;
    end if;

    if p_new_status in ('delivering','delivered') and v_type <> 'delivery' then
        raise exception 'update_order_status: statuses delivering/delivered only for delivery orders';
    end if;

    if p_new_status in ('delivering','delivered') and v_courier_id is null then
        raise exception 'update_order_status: courier must be assigned before status %', p_new_status;
    end if;

    update orders
       set status = p_new_status,
           delivered_at = case when p_new_status in ('delivered','completed') then coalesce(delivered_at, now()) else delivered_at end
     where id = p_order_id;
end;$$;

create or replace function process_payment(
    p_order_id integer,
    p_method text default 'cash'
) returns integer
language plpgsql as $$
declare
    v_amount numeric(14,2);
    v_payment_id integer;
    v_status text;
begin
    if exists (select 1 from payments where order_id = p_order_id) then
        raise exception 'process_payment: payment already exists for order %', p_order_id;
    end if;

    select status, total_amount
      into v_status, v_amount
      from orders
     where id = p_order_id;
    if not found then
        raise exception 'process_payment: order % not found', p_order_id;
    end if;

    if lower(coalesce(v_status, '')) = 'cancelled' then
        raise exception 'process_payment: order % is cancelled', p_order_id;
    end if;
    if v_amount <= 0 then
        raise exception 'process_payment: order % has non-positive total (%.2f)', p_order_id, v_amount;
    end if;

    insert into payments(order_id, method, amount, success)
    values (p_order_id, lower(p_method), v_amount, true)
    returning id into v_payment_id;

    -- После успешной оплаты переводим заказ в статус confirmed только если он ещё pending
    update orders
       set status = case when lower(coalesce(v_status, '')) = 'pending' then 'confirmed' else status end,
           updated_at = now()
     where id = p_order_id;

    return v_payment_id;
end;$$;

drop function if exists get_kitchen_queue();

create or replace function get_kitchen_queue()
returns table (
    order_id integer,
    created_at timestamp,
    status text,
    preparing_at timestamp,
    ready_at timestamp,
    cooking_duration_seconds integer,
    items jsonb
)
language plpgsql as $$
begin
    return query
    select o.id,
           o.created_at,
           o.status::text,
           o.preparing_at,
           o.ready_at,
           o.cooking_duration_seconds,
           (
             select jsonb_agg(jsonb_build_object(
                        'menu_item_id', oi.menu_item_id,
                        'name', mi.name,
                        'quantity', oi.quantity,
                        'note', oi.note
                    ) order by oi.id)
               from order_items oi
               join menu_items mi on mi.id = oi.menu_item_id
              where oi.order_id = o.id
          ) as items
      from orders o
     where lower(o.status) in ('confirmed','preparing','ready')
     order by o.created_at asc;
end;$$;

create or replace function sales_summary(
    p_from timestamp,
    p_to   timestamp
) returns table (
    from_ts timestamp,
    to_ts   timestamp,
    orders_cnt integer,
    revenue numeric(14,2),
    avg_ticket numeric(14,2)
)
language plpgsql as $$
declare
    v_from timestamp;
    v_to   timestamp;
begin
    v_from := coalesce(p_from, timestamp '1970-01-01 00:00:00');
    v_to := coalesce(p_to, now());

    return query
    with paid_orders as (
        select p.order_id
          from payments p
         where p.success = true
           and p.paid_at >= v_from
           and p.paid_at <= v_to
    ),
    order_totals as (
        select po.order_id,
               coalesce(sum(oi.total_price), 0)::numeric(14,2) as order_total
          from paid_orders po
     left join order_items oi on oi.order_id = po.order_id
         group by po.order_id
    )
    select p_from,
           p_to,
           count(*)::int as orders_cnt,
           coalesce(sum(ot.order_total), 0)::numeric(14,2) as revenue,
           case when count(*) > 0 then round(sum(ot.order_total) / count(*), 2) else 0 end::numeric(14,2) as avg_ticket
      from order_totals ot;
end;$$;

create or replace function sales_by_employee(
    p_from timestamp,
    p_to   timestamp
) returns table (
    employee_id integer,
    employee_name text,
    orders_cnt integer,
    revenue numeric(14,2),
    avg_ticket numeric(14,2)
)
language plpgsql as $$
declare
    v_from timestamp;
    v_to   timestamp;
begin
    v_from := coalesce(p_from, timestamp '1970-01-01 00:00:00');
    v_to := coalesce(p_to, now());

    return query
    with paid_orders as (
        select o.id as order_id,
               o.accepted_by_employee_id as employee_id
          from orders o
          join payments p on p.order_id = o.id
                         and p.success = true
                         and p.paid_at >= v_from
                         and p.paid_at <= v_to
         where o.accepted_by_employee_id is not null
    ),
    order_totals as (
        select po.order_id,
               po.employee_id,
               coalesce(sum(oi.total_price), 0)::numeric(14,2) as order_total
          from paid_orders po
     left join order_items oi on oi.order_id = po.order_id
         group by po.order_id, po.employee_id
    )
    select e.id as employee_id,
           e.full_name::text as employee_name,
           count(*)::int as orders_cnt,
           coalesce(sum(ot.order_total), 0)::numeric(14,2) as revenue,
           case when count(*) > 0 then round(sum(ot.order_total) / count(*), 2) else 0 end::numeric(14,2) as avg_ticket
      from order_totals ot
      join employees e on e.id = ot.employee_id
     group by e.id, e.full_name
     order by revenue desc, orders_cnt desc, employee_name asc;
end;$$;

create or replace function sales_by_time_of_day(
    p_from timestamp,
    p_to   timestamp
) returns table (
    bucket text,
    orders_cnt integer,
    revenue numeric(14,2),
    avg_ticket numeric(14,2)
)
language plpgsql as $$
declare
    v_from timestamp;
    v_to   timestamp;
begin
    v_from := coalesce(p_from, timestamp '1970-01-01 00:00:00');
    v_to := coalesce(p_to, now());

    return query
    with paid_orders as (
        select o.id as order_id,
               case
                   when extract(hour from p.paid_at) >= 6 and extract(hour from p.paid_at) < 12 then 'morning'
                   when extract(hour from p.paid_at) >= 12 and extract(hour from p.paid_at) < 18 then 'day'
                   when extract(hour from p.paid_at) >= 18 and extract(hour from p.paid_at) < 24 then 'evening'
                   else 'night'
               end as bucket
          from orders o
          join payments p on p.order_id = o.id
                         and p.success = true
                         and p.paid_at >= v_from
                         and p.paid_at <= v_to
    ),
    order_totals as (
        select po.order_id,
               po.bucket,
               coalesce(sum(oi.total_price), 0)::numeric(14,2) as order_total
          from paid_orders po
     left join order_items oi on oi.order_id = po.order_id
         group by po.order_id, po.bucket
    )
    select ot.bucket,
           count(*)::int as orders_cnt,
           coalesce(sum(ot.order_total), 0)::numeric(14,2) as revenue,
           case when count(*) > 0 then round(sum(ot.order_total) / count(*), 2) else 0 end::numeric(14,2) as avg_ticket
      from order_totals ot
     group by ot.bucket
     order by case ot.bucket
                  when 'morning' then 1
                  when 'day' then 2
                  when 'evening' then 3
                  when 'night' then 4
                  else 5
              end;
end;$$;

create or replace function financial_summary(
    p_from timestamp,
    p_to   timestamp
) returns table (
    from_ts timestamp,
    to_ts   timestamp,
    revenue numeric(14,2),
    ingredient_expenses numeric(14,2),
    salary_expenses numeric(14,2),
    profit numeric(14,2)
)
language plpgsql as $$
declare
    v_from timestamp;
    v_to   timestamp;
    v_revenue numeric(14,2);
    v_ing_exp numeric(14,2);
    v_sal_exp numeric(14,2);
begin
    v_from := coalesce(p_from, timestamp '1970-01-01 00:00:00');
    v_to := coalesce(p_to, now());

    with paid_orders as (
        select p.order_id
          from payments p
         where p.success = true
           and p.paid_at >= v_from
           and p.paid_at <= v_to
    ),
    order_totals as (
        select po.order_id,
               coalesce(sum(oi.total_price), 0)::numeric(14,2) as order_total
          from paid_orders po
     left join order_items oi on oi.order_id = po.order_id
         group by po.order_id
    )
    select coalesce(sum(ot.order_total), 0)::numeric(14,2)
      into v_revenue
      from order_totals ot;

    select coalesce(round(sum(it.delta * i.cost_per_unit), 2), 0)::numeric(14,2)
      into v_ing_exp
      from inventory_transactions it
      join ingredients i on i.id = it.ingredient_id
     where it.delta > 0
       and lower(coalesce(it.source, '')) = 'manual'
       and it.created_at >= v_from
       and it.created_at <= v_to;

    select coalesce(sum(sp.amount), 0)::numeric(14,2)
      into v_sal_exp
      from salary_payments sp
     where sp.paid_at >= v_from
       and sp.paid_at <= v_to;

    return query
    select p_from,
           p_to,
           v_revenue,
           v_ing_exp,
           v_sal_exp,
           (v_revenue - v_ing_exp - v_sal_exp)::numeric(14,2);
end;$$;

create or replace function top_menu_items(
    p_from timestamp,
    p_to   timestamp,
    p_limit integer default 10
) returns table (
    menu_item_id integer,
    name text,
    quantity bigint,
    revenue numeric(14,2)
)
language plpgsql as $$
declare
    v_from timestamp;
    v_to   timestamp;
begin
    v_from := coalesce(p_from, timestamp '1970-01-01 00:00:00');
    v_to := coalesce(p_to, now());

    return query
    select oi.menu_item_id,
           mi.name::text,
           sum(oi.quantity) as quantity,
           sum(oi.unit_price * oi.quantity)::numeric(14,2) as revenue
      from order_items oi
      join orders o   on o.id = oi.order_id
      join payments p on p.order_id = o.id
                      and p.success = true
                      and p.paid_at >= v_from
                      and p.paid_at <= v_to
      join menu_items mi on mi.id = oi.menu_item_id
     group by oi.menu_item_id, mi.name
     order by quantity desc
     limit p_limit;
end;$$;

create or replace function low_stock(threshold_factor double precision default 1.0)
returns table (
    ingredient_id integer,
    name text,
    quantity numeric,
    min_threshold numeric
)
language plpgsql as $$
begin
    return query
    select i.id, i.name::text, coalesce(ir.quantity, 0), i.min_threshold
      from ingredients i
 left join inventory_records ir on ir.ingredient_id = i.id
     where coalesce(ir.quantity, 0) <= i.min_threshold * threshold_factor
     order by (coalesce(ir.quantity, 0) - i.min_threshold) asc;
end;$$;

create or replace function adjust_inventory(
    p_ingredient_id integer,
    p_delta numeric,
    p_reason text default null,
    p_employee_id integer default null,
    p_order_id integer default null,
    p_source text default 'manual'
) returns void
language plpgsql as $$
declare
    v_current numeric(14,3);
    v_new numeric(14,3);
    v_name text;
    v_reason text;
    v_source text;
begin
    if p_delta is null then
        raise exception 'adjust_inventory: delta cannot be null';
    end if;
    if p_delta = 0 then
        return;
    end if;

    select i.name
      into v_name
      from ingredients i
     where i.id = p_ingredient_id;
    if not found then
        raise exception 'adjust_inventory: ingredient % not found', p_ingredient_id;
    end if;

    insert into inventory_records(ingredient_id, quantity)
    values (p_ingredient_id, 0)
    on conflict (ingredient_id) do nothing;

    select ir.quantity
      into v_current
      from inventory_records ir
     where ir.ingredient_id = p_ingredient_id
     for update;

    v_new := v_current + p_delta;
    if v_new < 0 then
        raise exception 'adjust_inventory: insufficient stock for ingredient % (id=%) current=%, delta=%',
            v_name,
            p_ingredient_id,
            v_current,
            p_delta;
    end if;

    update inventory_records
       set quantity = v_new,
           last_updated = now()
     where ingredient_id = p_ingredient_id;

    v_reason := nullif(trim(coalesce(p_reason, '')), '');
    v_source := coalesce(nullif(trim(coalesce(p_source, '')), ''), 'manual');

    if to_regclass('public.inventory_transactions') is not null then
        execute 'insert into inventory_transactions(ingredient_id, delta, reason, source, employee_id, order_id, created_at)
                 values ($1, $2, $3, $4, $5, $6, now())'
            using p_ingredient_id,
                  p_delta,
                  v_reason,
                  v_source,
                  p_employee_id,
                  p_order_id;
    end if;
end;$$;

create or replace function restock_ingredient(
    p_ingredient_id integer,
    p_delta numeric
) returns void
language plpgsql as $$
begin
    perform adjust_inventory(p_ingredient_id, p_delta);
end;$$;

create or replace function trg_orders_inventory_adjust()
returns trigger
language plpgsql as $$
begin
    if new.status is distinct from old.status then
        if lower(old.status) = 'pending' and lower(new.status) = 'confirmed' then
            perform consume_ingredients_for_order(new.id);
        elsif lower(new.status) = 'cancelled' and lower(old.status) <> 'pending' then
            perform restore_ingredients_for_order(new.id);
        end if;
    end if;
    return new;
end;$$;

drop trigger if exists orders_inventory_adjust on orders;
create trigger orders_inventory_adjust
before update of status on orders
for each row execute function trg_orders_inventory_adjust();
