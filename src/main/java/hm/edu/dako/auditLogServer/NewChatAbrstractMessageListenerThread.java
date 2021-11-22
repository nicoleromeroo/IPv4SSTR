package hm.edu.dako.auditLogServer;


import hm.edu.dako.chatClient.ClientUserInterface;
import hm.edu.dako.chatClient.SharedClientData;
import hm.edu.dako.chatCommon.ExceptionHandler;
import hm.edu.dako.connection.Connection;
import hm.edu.dako.pdu.ChatPDU;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

abstract class NewChatAbstractMessageListenerThread extends Thread {

    private static final Logger log = LogManager.getLogger(hm.edu.dako.auditLogServer.NewChatAbstractMessageListenerThread.class);

    // Kennzeichen zum Beenden der Bearbeitung
    protected boolean finished = false;

    // Verbindung zum Server
    protected Connection connection;

    // Schnittstelle zum User-Interface
    protected ClientUserInterface userInterface;

    // Gemeinsame Daten zwischen Client-Thread und Message-Processing-Thread
    protected SharedClientData sharedClientData;

    public NewChatAbstractMessageListenerThread(ClientUserInterface userInterface, Connection con,
                                         SharedClientData sharedData) {

        this.userInterface = userInterface;
        this.connection = con;
        this.sharedClientData = sharedData;
    }

    /**
     * Event vom Server zur Veraenderung der UserListe (eingeloggte Clients) verarbeiten
     *
     * @param receivedPdu Empfangene PDU
     */
    protected void handleUserListEvent(ChatPDU receivedPdu) {

        log.debug(
                "Login- oder Logout-Event-PDU fuer " + receivedPdu.getUserName() + " empfangen");

        // Neue Userliste zur Darstellung an User Interface uebergeben
        log.debug("Empfangene Userliste: " + receivedPdu.getClients());
        userInterface.setUserList(receivedPdu.getClients());
    }

    /**
     * Chat-PDU empfangen
     *
     * @return Empfangene ChatPDU
     * @throws Exception Verbindungsfehler
     */
    protected ChatPDU receive() throws Exception {
        try {
            return (ChatPDU) connection.receive();
        } catch (Exception e) {
            ExceptionHandler.logException(e);
        }
        return null;
    }

    /**
     * Aktion zur Behandlung ankommender ChatMessageEvents.
     *
     * @param receivedPdu Ankommende PDU
     */
    protected abstract void chatMessageResponseAction(ChatPDU receivedPdu);

    /**
     * Aktion zur Behandlung ankommender ChatMessageResponses.
     *
     * @param receivedPdu Ankommende PDU
     */
    protected abstract void chatMessageEventAction(ChatPDU receivedPdu);

    /**
     * Aktion zur Behandlung ankommender Login-Responsesd.
     *
     * @param receivedPdu Ankommende PDU
     */
    protected abstract void loginResponseAction(ChatPDU receivedPdu);

    /**
     * Aktion zur Behandlung ankommender Login-Events.
     *
     * @param receivedPdu Ankommende PDU
     */
    protected abstract void loginEventAction(ChatPDU receivedPdu);

    /**
     * Aktion zur Behandlung ankommender Logout-Events.
     *
     * @param receivedPdu Ankommende PDU
     */
    protected abstract void logoutEventAction(ChatPDU receivedPdu);

    /**
     * Aktion zur Behandlung ankommender Logout-Responses.
     *
     * @param receivedPdu Ankommende PDU
     */
    protected abstract void logoutResponseAction(ChatPDU receivedPdu);
}

