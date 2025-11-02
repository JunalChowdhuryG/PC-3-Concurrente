package com.shibasito;

public class Main {
    public static void main(String[] args) {
        String rabbitHost = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");

        System.out.println("=============================================");
        System.out.println("INICIANDO CLIENTE DE PRUEBA CLI (LP3 - Java)");
        System.out.println("Conectando a RabbitMQ en: " + rabbitHost);
        System.out.println("=============================================");

        try (RpcClient rpcClient = new RpcClient(rabbitHost)) {

            // --- Prueba 1: Validar RENIEC (LP2) ---
            System.out.println("\n--- PRUEBA 1: RENIEC ---");
            String dniPayload = "{\"dni\": \"12345678\"}";
            System.out.println("-> Enviando Petición: reniec.validar | Body: " + dniPayload);
            String reniecResponse = rpcClient.call("reniec.validar", dniPayload);
            System.out.println("<- Respuesta Recibida: " + reniecResponse);

            // --- Prueba 2: Consultar Saldo (LP1) ---
            System.out.println("\n--- PRUEBA 2: BANCO ---");
            String saldoPayload = "{\"idCliente\": \"CL001\"}";
            System.out.println("-> Enviando Petición: banco.consulta.saldo | Body: " + saldoPayload);
            String bancoResponse = rpcClient.call("banco.consulta.saldo", saldoPayload);
            System.out.println("<- Respuesta Recibida: " + bancoResponse);

            System.out.println("\n=============================================");
            System.out.println("PRUEBAS COMPLETADAS EXITOSAMENTE.");
            System.out.println("=============================================");

        } catch (Exception e) {
            System.err.println("Error en el cliente RPC: " + e.getMessage());
            e.printStackTrace();
        }
    }
}