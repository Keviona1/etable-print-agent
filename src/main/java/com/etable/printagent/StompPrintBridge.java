package com.etable.printagent;



// --- SPRING AND STANDARD JAVA IMPORTS ---
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.Style;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.github.anastaciocintra.output.PrinterOutputStream;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

public class StompPrintBridge {

    private static String WEBSOCKET_URI;
    private static String[] SECTORS_TO_SUBSCRIBE; // Changed from single String
    private static String PRINTER_NAME;

    public static void main(String[] args) {
        String wsUriBase = Config.get("websocket.uri");
        String sectorsConfig = Config.get("sectors");
        String secretToken = Config.get("secret.token");
        PRINTER_NAME = Config.get("printer.name");

        if (wsUriBase == null || sectorsConfig == null || sectorsConfig.trim().isEmpty() || secretToken == null || PRINTER_NAME == null) {
            System.err.println("FATAL: One or more required properties are missing from agent.properties.");
            return;
        }

        // The URL should NOT have the query parameter
        WEBSOCKET_URI = wsUriBase;
        SECTORS_TO_SUBSCRIBE = sectorsConfig.split(",");

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // --- THIS IS THE FINAL, CORRECT WAY TO CONNECT ---
        // 1. Create WebSocketHttpHeaders for the INITIAL handshake.
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add("printer-token", secretToken);

        // 2. STOMP headers can be empty for this authentication method.
        StompHeaders connectHeaders = new StompHeaders();

        // 3. Create the session handler.
        StompSessionHandler sessionHandler = new MyStompSessionHandler();
        // ----------------------------------------------------

        System.out.println("--- e-Table Print Agent v1.0 (Multi-Sector) ---");
        System.out.println("Attempting to connect to: " + WEBSOCKET_URI);
        System.out.println("Printer: " + PRINTER_NAME);
        System.out.println("Listening for sectors: " + String.join(", ", SECTORS_TO_SUBSCRIBE));

        try {
            // We now pass the handshakeHeaders in the second argument. This is the fix.
            stompClient.connect(
                    WEBSOCKET_URI,
                    handshakeHeaders, // <-- THE TOKEN IS NOW IN THE CORRECT PLACE
                    connectHeaders,
                    sessionHandler
            ).get();

            System.out.println("\nSUCCESS: Connected to server. Listening for print jobs...");
            System.out.println("This window must remain open. Press ENTER to exit.");
            new Scanner(System.in).nextLine();

        } catch (Exception e) {
            System.err.println("\nERROR: Connection failed.");
            if(e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            } else {
                e.printStackTrace();
            }
        }
    }
    private static class MyStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("Session established. Subscribing to topics...");
            // --- CHANGE 4: Loop through each sector and subscribe to its topic ---
            for (String sector : SECTORS_TO_SUBSCRIBE) {
                String trimmedSector = sector.trim(); // Remove any extra spaces
                if (!trimmedSector.isEmpty()) {
                    String topic = "/topic/print-jobs/" + trimmedSector.toLowerCase();
                    session.subscribe(topic, this);
                    System.out.println(" -> Subscribed to: " + topic);
                }
            }
        }
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            System.err.println("Connection lost: " + exception.getMessage() + ". Please restart the agent.");
        }
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return com.etable.printagent.WebSocketMessage.class;
        }
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            com.etable.printagent.WebSocketMessage msg = (com.etable.printagent.WebSocketMessage) payload;
            if ("print_job".equals(msg.getType())) {
                printTicket(msg.getPayload());
            }
        }
    }

    // --- HELPER METHOD TO FIND THE PRINTER (CORRECT FOR v4.1.0) ---
    private static Optional<PrintService> findPrintService(String printerName) {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(printServices)
                .filter(service -> service.getName().equalsIgnoreCase(printerName))
                .findFirst();
    }

    private static void printTicket(PrintJobDto job) {
        System.out.println("\nReceived job! Printing order #" + job.getOrderId() + " for table " + job.getTableNumber());
        try {
            Optional<PrintService> optionalPrintService = findPrintService(PRINTER_NAME);
            if (optionalPrintService.isEmpty()) {
                System.err.println("--- PRINTER NOT FOUND ---");
                System.err.println("Could not find a printer named: '" + PRINTER_NAME + "'");
                System.err.println("Available printers on this computer are:");
                for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
                    System.err.println(" -> " + service.getName());
                }
                return;
            }
            PrintService printService = optionalPrintService.get();

            OutputStream outputStream = new PrinterOutputStream(printService);
            EscPos escpos = new EscPos(outputStream);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");

            // --- THIS BLOCK IS NOW SAFER ---
            // 1. Safely handle a null table name
            String tableName = (job.getTableNumber() != null && !job.getTableNumber().trim().isEmpty()) ? job.getTableNumber() : "New Order";

            escpos.getStyle()
                    .setBold(true)
                    .setFontSize(Style.FontSize._2, Style.FontSize._2);
            escpos.writeLF(tableName); // Prints "New Order" if table name is null
            escpos.getStyle().reset();

            // 2. Safely handle a null order ID
            String orderIdText = (job.getOrderId() != null) ? "#" + job.getOrderId() : "";
            escpos.writeLF("Order " + orderIdText);
            // --- END OF SAFE BLOCK ---

            escpos.writeLF(LocalDateTime.now().format(formatter));
            escpos.feed(1).writeLF("----------------------------------------");

            for (OrderItemDTO item : job.getItems()) {
                escpos.getStyle().setBold(true);

                // 3. Safely handle a null article name
                String articleName = (item.getArticleName() != null) ? item.getArticleName() : "Unknown Article";
                escpos.writeLF(String.format("%dx %s", item.getQuantity(), articleName));
                escpos.getStyle().setBold(false);

                if (item.getNote() != null && !item.getNote().trim().isEmpty()) {
                    escpos.writeLF("  >> " + item.getNote());
                }
            }
            escpos.feed(1).writeLF("----------------------------------------");
            escpos.feed(4).cut(EscPos.CutMode.FULL);
            escpos.close();

            System.out.println("Successfully sent job to printer: " + PRINTER_NAME);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to print ticket: " + e.getMessage());
            e.printStackTrace();
        }
    }
}