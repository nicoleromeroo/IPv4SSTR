package hm.edu.dako.chatBenchmarking;

import hm.edu.dako.chatClient.ClientUserInterface;
import hm.edu.dako.connection.ConnectionFactory;

/**
 * Uebernimmt die Konfiguration und die Erzeugung bestimmter Client-Typen fuer das Benchmarking. Siehe {@link
 * hm.edu.dako.chatBenchmarking.UserInterfaceInputParameters}
 * Dies beinhaltet die {@link ConnectionFactory}, die Adressen, Ports, Denkzeit etc.
 */
public final class BenchmarkingClientFactory {

    private BenchmarkingClientFactory() {
    }

    public static Runnable getClient(ClientUserInterface userInterface,
                                     UserInterfaceInputParameters param, int numberOfClient,
                                     SharedClientStatistics sharedData,
                                     BenchmarkingClientUserInterface benchmarkingGui) {
        try {

            // Derzeit sind TCPAdvancedImplementation, UDPAdvancedImplementation nicht implementiert
            return switch (param.getChatServerImplementationType()) {
                case TCPSimpleImplementation -> new BenchmarkingClientImpl(userInterface,
                        benchmarkingGui, param.getChatServerImplementationType(), param.getRemoteServerPort(),
                        param.getRemoteServerAddress(), numberOfClient, param.getMessageLength(),
                        param.getNumberOfMessages(), param.getClientThinkTime(),
                        param.getNumberOfRetries(), param.getResponseTimeout(), sharedData);
                default -> throw new RuntimeException(
                        "Unbekannter Implementierungstyp: " + param.getChatServerImplementationType());
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}