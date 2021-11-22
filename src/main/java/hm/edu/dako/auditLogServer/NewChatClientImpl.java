package hm.edu.dako.auditLogServer;

import hm.edu.dako.chatClient.AbstractChatClient;
import hm.edu.dako.chatClient.ClientUserInterface;
import hm.edu.dako.chatClient.SimpleMessageListenerThreadImpl;
import hm.edu.dako.chatCommon.ExceptionHandler;
import hm.edu.dako.chatCommon.SystemConstants;


/**
 * Verwaltet eine Verbindung zum Server.
 * @author Peter Mandl
 */
public class NewChatClientImpl extends AbstractChatClient {

    /**
     * Konstruktor
     * @param userInterface Schnittstelle zum User-Interface
     * @param serverPort Portnummer des Servers
     * @param remoteServerAddress IP-Adresse/Hostname des Servers
     * @param serverType Typ des Servers
     */

    public NewChatClientImpl(ClientUserInterface userInterface, int serverPort,
                      String remoteServerAddress, String serverType) {

        super(userInterface, serverPort, remoteServerAddress);
        this.serverPort = serverPort;
        this.remoteServerAddress = remoteServerAddress;

        Thread.currentThread().setName("Client");
        threadName = Thread.currentThread().getName();

        try {

            if (serverType.equals(SystemConstants.IMPL_TCP_SIMPLE)) {
                // Simple TCP Server erzeugen, derzeit gibt es nur den einen
                messageListenerThread = new SimpleMessageListenerThreadImpl(userInterface,
                        connection, sharedClientData);
            }
            messageListenerThread.start();
        } catch (Exception e) {
            ExceptionHandler.logException(e);
        }
    }

}