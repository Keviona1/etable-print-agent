package com.etable.printagent;

import java.util.List;

// This class must match the PrintJobDto on your backend
public class PrintJobDto {
    private Long orderId;
    private String tableNumber;
    private List<OrderItemDTO> items;

    // Getters and Setters
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getTableNumber() { return tableNumber; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }
    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }
}