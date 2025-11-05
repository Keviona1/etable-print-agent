package com.etable.printagent;

// This must match the OrderItemDTO on your backend
public class OrderItemDTO {
    private String articleName;
    private Integer quantity;
    private String note;

    // Getters and Setters
    public String getArticleName() { return articleName; }
    public void setArticleName(String articleName) { this.articleName = articleName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}