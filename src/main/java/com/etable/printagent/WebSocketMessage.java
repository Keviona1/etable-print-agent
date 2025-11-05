package com.etable.printagent;

public class WebSocketMessage {
    private String type;
    private PrintJobDto payload;
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public PrintJobDto getPayload() { return payload; }
    public void setPayload(PrintJobDto payload) { this.payload = payload; }
}