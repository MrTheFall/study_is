package com.krusty.crab.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SequenceSyncConfig {
    
    private final JdbcTemplate jdbcTemplate;
    
    @PostConstruct
    public void syncSequences() {
        log.info("Synchronizing database sequences...");
        try {
            String sql = """
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
                """;
            
            jdbcTemplate.execute(sql);
            log.info("Database sequences synchronized successfully");
        } catch (Exception e) {
            log.error("Failed to synchronize database sequences", e);
        }
    }
}

