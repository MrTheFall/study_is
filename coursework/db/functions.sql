create or replace function place_order(
    p_client_id integer,
    p_type text,
    p_delivery_address text,
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

    insert into orders (client_id, type, status, delivery_address)
    values (p_client_id, lower(p_type), 'pending', p_delivery_address)
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
    v_allowed boolean := false;
begin
    select status, type into v_cur, v_type from orders where id = p_order_id;
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
begin
    if exists (select 1 from payments where order_id = p_order_id) then
        raise exception 'process_payment: payment already exists for order %', p_order_id;
    end if;

    select total_amount into v_amount from orders where id = p_order_id;
    if v_amount is null then
        raise exception 'process_payment: order % not found', p_order_id;
    end if;
    if v_amount <= 0 then
        raise exception 'process_payment: order % has non-positive total (%.2f)', p_order_id, v_amount;
    end if;

    insert into payments(order_id, method, amount, success)
    values (p_order_id, lower(p_method), v_amount, true)
    returning id into v_payment_id;

    -- После успешной оплаты переводим заказ в статус confirmed, чтобы он попал на кухню
    update orders
       set status = 'confirmed',
           updated_at = now()
     where id = p_order_id;

    return v_payment_id;
end;$$;

create or replace function get_kitchen_queue()
returns table (
    order_id integer,
    created_at timestamp,
    status text,
    items jsonb
)
language plpgsql as $$
begin
    return query
    select o.id,
           o.created_at,
           o.status::text,
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
begin
    return query
    select p_from,
           p_to,
           count(*)::int as orders_cnt,
           coalesce(sum(p.amount),0)::numeric(14,2) as revenue,
           case when count(*)>0 then round(sum(p.amount)/count(*), 2) else 0 end::numeric(14,2) as avg_ticket
      from payments p
     where p.success = true
       and (p.paid_at between p_from and p_to);
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
begin
    return query
    select oi.menu_item_id,
           mi.name::text,
           sum(oi.quantity) as quantity,
           sum(oi.unit_price * oi.quantity)::numeric(14,2) as revenue
      from order_items oi
      join orders o   on o.id = oi.order_id
      join payments p on p.order_id = o.id and p.success = true and p.paid_at between p_from and p_to
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

create or replace function restock_ingredient(
    p_ingredient_id integer,
    p_delta numeric
) returns void
language plpgsql as $$
begin
    if p_delta = 0 then
        return;
    end if;
    update inventory_records
       set quantity = greatest(0, quantity + p_delta),
           last_updated = now()
     where ingredient_id = p_ingredient_id;
    if not found then
        insert into inventory_records(ingredient_id, quantity)
        values(p_ingredient_id, greatest(0, p_delta));
    end if;
end;$$;
