package com.shibasito;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main extends Application {

    private static final String RABBITMQ_HOST_IP = "192.168.0.9"; // (Tu IP)

    private TextArea logArea = new TextArea();
    private ObjectMapper jsonMapper = new ObjectMapper();

    // --- Campos de la GUI ---
    private TextField reniecDniField = new TextField("12345678");
    private TextField bancoIdField = new TextField("CL001"); // Cliente Origen
    private TextField prestamoMontoField = new TextField("500.00");
    
    // --- CAMPOS DE TRANSFERENCIA (ACTUALIZADOS) ---
    private TextField transferDestinoField = new TextField("CL002"); // <-- CAMBIO: Cliente Destino
    private TextField transferMontoField = new TextField("50.00");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Banco Shibasito - Cliente Desktop (JavaFX)");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(200);

        // --- (Panel RENIEC y Panel BANCO no cambian) ---
        GridPane reniecPane = new GridPane();
        reniecPane.setPadding(new Insets(10));
        reniecPane.setHgap(10);
        reniecPane.setVgap(10);
        Label reniecLabel = new Label("DNI:");
        Button reniecButton = new Button("Validar Identidad (LP2)");
        reniecPane.add(reniecLabel, 0, 0);
        reniecPane.add(reniecDniField, 1, 0);
        reniecPane.add(reniecButton, 2, 0);

        GridPane bancoPane = new GridPane();
        bancoPane.setPadding(new Insets(10));
        bancoPane.setHgap(10);
        bancoPane.setVgap(10);
        Label bancoIdLabel = new Label("ID Cliente (Origen):");
        Button saldoButton = new Button("Consultar Saldo");
        Button historialButton = new Button("Ver Historial");
        bancoPane.add(bancoIdLabel, 0, 0);
        bancoPane.add(bancoIdField, 1, 0, 2, 1);
        bancoPane.add(saldoButton, 1, 1);
        bancoPane.add(historialButton, 2, 1);

        // --- PANEL TRANSFERENCIA (ACTUALIZADO) ---
        GridPane transferPane = new GridPane();
        transferPane.setPadding(new Insets(10));
        transferPane.setHgap(10);
        transferPane.setVgap(10);
        // --- CAMBIO: Etiqueta ---
        Label transferDestinoLabel = new Label("Cliente Destino (Yape):");
        Label transferMontoLabel = new Label("Monto:");
        Button transferButton = new Button("Transferir (Sim Yape)");
        transferPane.add(transferDestinoLabel, 0, 0);
        transferPane.add(transferDestinoField, 1, 0);
        transferPane.add(transferMontoLabel, 0, 1);
        transferPane.add(transferMontoField, 1, 1);
        transferPane.add(transferButton, 2, 1);
        
        // --- (Panel PRÉSTAMO no cambia) ---
        GridPane prestamoPane = new GridPane();
        prestamoPane.setPadding(new Insets(10));
        prestamoPane.setHgap(10);
        prestamoPane.setVgap(10);
        Label prestamoMontoLabel = new Label("Monto Préstamo:");
        Button prestamoButton = new Button("Solicitar Préstamo");
        prestamoPane.add(prestamoMontoLabel, 0, 0);
        prestamoPane.add(prestamoMontoField, 1, 0);
        prestamoPane.add(prestamoButton, 2, 0);

        // --- (Lógica de botones RENIEC, Saldo, Historial, Préstamo no cambia) ---
        reniecButton.setOnAction(e -> {
            String dni = reniecDniField.getText();
            String payload = String.format("{\"dni\": \"%s\"}", dni);
            ejecutarRpc("reniec.validar", payload);
        });
        saldoButton.setOnAction(e -> {
            String idCliente = bancoIdField.getText();
            String payload = String.format("{\"idCliente\": \"%s\"}", idCliente);
            ejecutarRpc("banco.consulta.saldo", payload);
        });
        historialButton.setOnAction(e -> {
            String idCliente = bancoIdField.getText();
            String payload = String.format("{\"idCliente\": \"%s\"}", idCliente);
            ejecutarRpc("banco.historial", payload);
        });
        prestamoButton.setOnAction(e -> {
            String idCliente = bancoIdField.getText();
            String monto = prestamoMontoField.getText();
            String payload = String.format("{\"idCliente\": \"%s\", \"monto\": %s}", idCliente, monto);
            ejecutarRpc("banco.prestamo.solicitar", payload);
        });

        // --- LÓGICA BOTÓN TRANSFERIR (ACTUALIZADA) ---
        transferButton.setOnAction(e -> {
            String idClienteOrigen = bancoIdField.getText();
            // --- CAMBIO: Usar idClienteDestino ---
            String idClienteDestino = transferDestinoField.getText();
            String monto = transferMontoField.getText();
            
            // --- CAMBIO: Enviar 'idClienteDestino' ---
            String payload = String.format(
                "{\"idClienteOrigen\": \"%s\", \"idClienteDestino\": \"%s\", \"monto\": %s}",
                idClienteOrigen, idClienteDestino, monto
            );
            ejecutarRpc("banco.transferir", payload);
        });

        // Layout principal
        VBox root = new VBox(10, reniecPane, bancoPane, transferPane, prestamoPane, logArea);
        root.setPadding(new Insets(10));
        primaryStage.setScene(new Scene(root, 600, 550));
        primaryStage.show();

        log("Host de RabbitMQ configurado en: " + RABBITMQ_HOST_IP);
    }
    
    // --- (log, ejecutarRpc, y mostrarRespuesta no cambian) ---
    private void log(String message) {
        javafx.application.Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
    }

    private void ejecutarRpc(String routingKey, String payload) {
        log(String.format("-> [Hilo] Enviando Petición: %s | Body: %s", routingKey, payload));
        Task<String> rpcTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                try (RpcClient rpcClient = new RpcClient(RABBITMQ_HOST_IP)) {
                    Thread.sleep(1000); 
                    return rpcClient.call(routingKey, payload);
                }
            }
        };
        rpcTask.setOnSucceeded(e -> {
            String response = rpcTask.getValue();
            log("<- [Hilo] Respuesta Recibida: " + response);
            mostrarRespuesta(response);
        });
        rpcTask.setOnFailed(e -> {
            Throwable ex = rpcTask.getException();
            log("Error en hilo RPC: " + ex.getMessage());
            ex.printStackTrace();
            mostrarRespuesta("{\"status\":\"ERROR\", \"message\":\"" + ex.getMessage().lines().findFirst().orElse("Error desconocido") + "\"}");
        });
        new Thread(rpcTask).start();
    }

    private void mostrarRespuesta(String responseJson) {
        javafx.application.Platform.runLater(() -> {
            try {
                Object json = jsonMapper.readValue(responseJson, Object.class);
                String prettyJson = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                Alert.AlertType alertType = responseJson.contains("\"status\":\"ERROR\"") ? 
                                             Alert.AlertType.ERROR : Alert.AlertType.INFORMATION;
                Alert alert = new Alert(alertType);
                alert.setTitle("Respuesta del Servidor");
                alert.setHeaderText(null);
                TextArea textArea = new TextArea(prettyJson);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                alert.getDialogPane().setContent(textArea);
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Error al procesar respuesta");
                alert.setContentText(responseJson);
                alert.showAndWait();
            }
        });
    }
}