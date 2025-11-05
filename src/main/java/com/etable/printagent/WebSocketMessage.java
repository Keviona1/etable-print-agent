package com.etable.printagent;

// This must match the WebSocketMessage wrapper on your backend
public class WebSocketMessage {
    private String type;
    private PrintJobDto payload; // We specify the payload type here for simplicity

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public PrintJobDto getPayload() { return payload; }
    public void setPayload(PrintJobDto payload) { this.payload = payload; }
}