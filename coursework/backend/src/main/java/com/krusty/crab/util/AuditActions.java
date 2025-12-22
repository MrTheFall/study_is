package com.krusty.crab.util;

public final class AuditActions {

    public static final String EMPLOYEE_LOGIN = "employee_login";
    public static final String EMPLOYEE_CREATE = "employee_create";
    public static final String EMPLOYEE_UPDATE = "employee_update";
    public static final String EMPLOYEE_DELETE = "employee_delete";

    public static final String MENU_ITEM_CREATE = "menu_item_create";
    public static final String MENU_ITEM_UPDATE = "menu_item_update";
    public static final String MENU_ITEM_DELETE = "menu_item_delete";

    public static final String INVENTORY_ADJUST = "inventory_adjust";

    public static final String COURIER_CREATE = "courier_create";
    public static final String COURIER_UPDATE = "courier_update";
    public static final String COURIER_DELETE = "courier_delete";

    public static final String SHIFT_CREATE = "shift_create";
    public static final String SHIFT_UPDATE = "shift_update";
    public static final String SHIFT_DELETE = "shift_delete";
    public static final String SHIFT_ASSIGN = "shift_assign";
    public static final String SHIFT_UNASSIGN = "shift_unassign";

    public static final String ORDER_CREATE = "order_create";
    public static final String ORDER_STATUS_CHANGE = "order_status_change";
    public static final String ORDER_PAYMENT_METHOD_CHANGE = "order_payment_method_change";
    public static final String ORDER_COURIER_ASSIGN = "order_courier_assign";

    public static final String PAYMENT_PROCESS = "payment_process";

    public static final String SALARY_PAYMENT_CREATE = "salary_payment_create";

    private AuditActions() {}
}
