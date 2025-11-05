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

    // --- ¡¡CONFIGURACIÓN IMPORTANTE!! ---
    // Reemplaza esto con la IP de tu Wi-Fi/Ethernet (la que encontraste con 'ipconfig')
    // Por ejemplo: "192.168.1.10" o "10.237.90.216"
    private static final String RABBITMQ_HOST_IP = "192.168.0.9";

    private TextArea logArea = new TextArea();
    private ObjectMapper jsonMapper = new ObjectMapper();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Banco Shibasito - Cliente Desktop (JavaFX)");

        // Panel de Logs
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(200);

        // --- Panel de RENIEC (Sin cambios) ---
        GridPane reniecPane = new GridPane();
        reniecPane.setPadding(new Insets(10));
        reniecPane.setHgap(10);
        reniecPane.setVgap(10);
        Label reniecLabel = new Label("DNI:");
        TextField reniecDniField = new TextField("12345678");
        Button reniecButton = new Button("Validar Identidad (LP2)");
        reniecPane.add(reniecLabel, 0, 0);
        reniecPane.add(reniecDniField, 1, 0);
        reniecPane.add(reniecButton, 2, 0);

        // --- Panel de BANCO (Actualizado) ---
        GridPane bancoPane = new GridPane();
        bancoPane.setPadding(new Insets(10));
        bancoPane.setHgap(10);
        bancoPane.setVgap(10);
        
        Label bancoIdLabel = new Label("ID Cliente:");
        TextField bancoIdField = new TextField("CL001");
        
        Label montoLabel = new Label("Monto:");
        TextField montoField = new TextField("500.00"); // Campo para el préstamo

        Button saldoButton = new Button("Consultar Saldo");
        Button historialButton = new Button("Ver Historial");
        Button prestamoButton = new Button("Solicitar Préstamo");

        // Fila 1: ID Cliente
        bancoPane.add(bancoIdLabel, 0, 0);
        bancoPane.add(bancoIdField, 1, 0, 2, 1); // Ocupa 2 columnas
        // Fila 2: Botones de consulta
        bancoPane.add(saldoButton, 1, 1);
        bancoPane.add(historialButton, 2, 1);
        // Fila 3: Préstamo
        bancoPane.add(montoLabel, 0, 2);
        bancoPane.add(montoField, 1, 2);
        bancoPane.add(prestamoButton, 2, 2);


        // --- Lógica de los Botones ---

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

        // --- NUEVA LÓGICA DE BOTONES ---
        
        historialButton.setOnAction(e -> {
            String idCliente = bancoIdField.getText();
            String payload = String.format("{\"idCliente\": \"%s\"}", idCliente);
            ejecutarRpc("banco.historial", payload);
        });

        prestamoButton.setOnAction(e -> {
            String idCliente = bancoIdField.getText();
            String monto = montoField.getText();
            // Validar que el monto sea un número
            try {
                Double.parseDouble(monto); // Solo para validar
            } catch (NumberFormatException ex) {
                log("Error: El monto debe ser un número válido.");
                mostrarRespuesta("{\"status\":\"ERROR\", \"message\":\"El monto debe ser un número válido (ej: 500.00)\"}");
                return;
            }
            String payload = String.format("{\"idCliente\": \"%s\", \"monto\": %s}", idCliente, monto);
            ejecutarRpc("banco.prestamo.solicitar", payload);
        });

        // Layout principal
        VBox root = new VBox(10, reniecPane, bancoPane, logArea);
        root.setPadding(new Insets(10));
        primaryStage.setScene(new Scene(root, 600, 450)); // Hice la ventana un poco más alta
        primaryStage.show();

        if (RABBITMQ_HOST_IP.equals("TU_IP_WIFI_AQUI")) {
            log("ERROR: Por favor edita el archivo Main.java y pon tu IP de Wi-Fi.");
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error de Configuración");
            alert.setHeaderText("IP no configurada");
            alert.setContentText("Por favor edita el archivo 'Main.java' (línea 17) y reemplaza 'TU_IP_WIFI_AQUI' con tu dirección IPv4 real.");
            alert.showAndWait();
        } else {
            log("Host de RabbitMQ configurado en: " + RABBITMQ_HOST_IP);
        }
    }

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
                // (Ya no necesitamos el Thread.sleep(1000) porque el backend ya está corriendo)
                try (RpcClient rpcClient = new RpcClient(RABBITMQ_HOST_IP)) {
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