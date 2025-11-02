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
    // Reemplaza esto con la IP de tu Wi-Fi (la que encontraste con 'ipconfig')
    private static final String RABBITMQ_HOST_IP = "10.237.90.216";

    private TextArea logArea = new TextArea();
    private ObjectMapper jsonMapper = new ObjectMapper(); // Para formatear el JSON

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

        // Panel de RENIEC
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

        // Panel de BANCO
        GridPane bancoPane = new GridPane();
        bancoPane.setPadding(new Insets(10));
        bancoPane.setHgap(10);
        bancoPane.setVgap(10);
        Label bancoLabel = new Label("ID Cliente:");
        TextField bancoIdField = new TextField("CL001");
        Button bancoButton = new Button("Consultar Saldo (LP1)");
        bancoPane.add(bancoLabel, 0, 0);
        bancoPane.add(bancoIdField, 1, 0);
        bancoPane.add(bancoButton, 2, 0);

        // --- Lógica de los Botones ---

        reniecButton.setOnAction(e -> {
            String dni = reniecDniField.getText();
            String payload = String.format("{\"dni\": \"%s\"}", dni);
            ejecutarRpc("reniec.validar", payload);
        });

        bancoButton.setOnAction(e -> {
            String idCliente = bancoIdField.getText();
            String payload = String.format("{\"idCliente\": \"%s\"}", idCliente);
            ejecutarRpc("banco.consulta.saldo", payload);
        });

        // Layout principal
        VBox root = new VBox(10, reniecPane, bancoPane, logArea);
        root.setPadding(new Insets(10));
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();

        log("Host de RabbitMQ configurado en: " + RABBITMQ_HOST_IP);
    }

    private void log(String message) {
        // Asegurarse de que el log se actualice en el hilo de la GUI
        javafx.application.Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
    }

    private void ejecutarRpc(String routingKey, String payload) {
        log(String.format("-> [Hilo] Enviando Petición: %s | Body: %s", routingKey, payload));

        // Crear una Tarea (Task) para ejecutar el RPC en un hilo de fondo
        // Esto evita que la GUI se congele
        Task<String> rpcTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Usar 'try-with-resources' para cerrar la conexión automáticamente
                try (RpcClient rpcClient = new RpcClient(RABBITMQ_HOST_IP)) {
                    return rpcClient.call(routingKey, payload);
                }
            }
        };

        // Qué hacer cuando la tarea termine
        rpcTask.setOnSucceeded(e -> {
            String response = rpcTask.getValue();
            log("<- [Hilo] Respuesta Recibida: " + response);
            mostrarRespuesta(response);
        });

        // Qué hacer si la tarea falla
        rpcTask.setOnFailed(e -> {
            Throwable ex = rpcTask.getException();
            log("Error en hilo RPC: " + ex.getMessage());
            ex.printStackTrace();
            mostrarRespuesta("{\"status\":\"ERROR\", \"message\":\"" + ex.getMessage() + "\"}");
        });

        // Iniciar el hilo
        new Thread(rpcTask).start();
    }

    private void mostrarRespuesta(String responseJson) {
        // Asegurarse de que el popup se muestre en el hilo de la GUI
        javafx.application.Platform.runLater(() -> {
            try {
                // Formatear el JSON para que se vea bonito
                Object json = jsonMapper.readValue(responseJson, Object.class);
                String prettyJson = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Respuesta del Servidor");
                alert.setHeaderText(null);
                alert.setContentText(prettyJson);
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