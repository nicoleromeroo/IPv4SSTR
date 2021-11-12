package hm.edu.dako.auditLogServer;

import hm.edu.dako.chatCommon.ClientConversationStatus;
import hm.edu.dako.chatCommon.ExceptionHandler;
import hm.edu.dako.chatServer.*;
import hm.edu.dako.connection.Connection;
import hm.edu.dako.connection.ConnectionTimeoutException;
import hm.edu.dako.connection.EndOfFileException;
import hm.edu.dako.pdu.AuditLogPduType;
import hm.edu.dako.pdu.ChatPDU;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Vector;

/**
 * Worker-Thread zur serverseitigen Bedienung einer Session mit einem Client. Jedem Chat-Client wird serverseitig ein
 * Worker-Thread zugeordnet.
 *
 * @author Peter Mandl
 * @version neue Version
 */

public class NewChatWorkerThreadImpl extends AbstractWorkerThread {

    private static final Logger newLog = LogManager.getLogger(SimpleChatWorkerThreadImpl.class);

    protected AuditLogConnection auditLogConnection;
    protected boolean auditLogServerEnabled;

    /**
     * Erzeugen eines Worker Threads fuer die Kommunikation mit einem Chat-Client
     *
     * @param con                Verbindung zum Chat-Client
     * @param clients            Liste der angemeldeten Chat-Clients
     * @param counter            Referenz auf diverse Zaehler fuer Tests
     * @param serverGuiInterface Referenz auf GUI des Chat-Servers
     */
    public NewChatWorkerThreadImpl(Connection con, SharedChatClientList clients,
                                   SharedServerCounter counter, ChatServerGuiInterface serverGuiInterface) {

        super(con, clients, counter, serverGuiInterface);
        this.auditLogConnection = null;
        this.auditLogServerEnabled = false;
        System.out.println("Workerthread ohne AuditLog erzeugt");
    }

    /**
     * Erzeugen eines Worker Threads fuer die Kommunikation mit einem Chat-Client. Zusätzlich wird
     * eine Verbindung zu einem AuditLog-Server uebergeben.
     *
     * @param con                Verbindung zum Chat-Client
     * @param clients            Liste der angemeldeten Chat-Clients
     * @param counter            Referenz auf diverse Zaehler fuer Tests
     * @param serverGuiInterface Referenz auf GUI des Chat-Servers
     * @param auditLogConnection Verbindung zum AuditLog-Server
     */
    public NewChatWorkerThreadImpl(Connection con, SharedChatClientList clients,
                                   SharedServerCounter counter, ChatServerGuiInterface serverGuiInterface,
                                   AuditLogConnection auditLogConnection) {

        super(con, clients, counter, serverGuiInterface);

        if (auditLogConnection != null) {
            this.auditLogServerEnabled = true;
            this.auditLogConnection = auditLogConnection;
            System.out.println("Workerthread mit AuditLog erzeugt");
        } else {
            this.auditLogServerEnabled = false;
        }
    }

    @Override
    public void run() {
        newLog.debug(
                "ChatWorker-Thread erzeugt, Threadname: " + Thread.currentThread().getName());
        while (!finished && !Thread.currentThread().isInterrupted()) {
            try {
                // Warte auf naechste Nachricht des Clients und fuehre
                // entsprechende Aktion aus
                handleIncomingMessage();
            } catch (Exception e) {
                newLog.error("Exception waehrend der Nachrichtenverarbeitung");
                ExceptionHandler.logException(e);
            }
        }
        newLog.debug(Thread.currentThread().getName() + " beendet sich");
        closeConnection();
    }

    /**
     * Senden eines Login-List-Update-Event an alle angemeldeten Clients
     *
     * @param pdu Zu sendende PDU
     */
    protected void sendLoginListUpdateEvent(ChatPDU pdu) {

        // Liste der eingeloggten bzw. sich einloggenden User ermitteln
        Vector<String> clientList = clients.getRegisteredClientNameList();

        newLog.debug("Aktuelle Clientliste, die an die Clients uebertragen wird: " + clientList);

        pdu.setClients(clientList);

        Vector<String> clientList2 = clients.getClientNameList();
        new Vector<>(clientList2).forEach(s -> {
            newLog.debug("Fuer " + s
                    + " wird Login- oder Logout-Event-PDU an alle aktiven Clients gesendet");
            ClientListEntry client = clients.getClient(s);
            try {
                if (client != null) {
                    // Aufbau einer Verbindung zum Client und senden der PDU
                    client.getConnection().send(pdu);
                    // EventCounter wird erhöht.
                    newLog.debug(
                            "Login- oder Logout-Event-PDU an " + client.getUserName() + " gesendet");
                    clients.incrNumberOfSentChatEvents(client.getUserName());
                    eventCounter.getAndIncrement();
                }
            } catch (Exception e) {
                newLog.error(
                        "Senden einer Login- oder Logout-Event-PDU an " + s + " nicht moeglich");
                ExceptionHandler.logException(e);
            }
        });
    }

    @Override
    protected void loginRequestAction(ChatPDU receivedPdu) {

        ChatPDU eventPDU;
        newLog.debug("Login-Request-PDU fuer " + receivedPdu.getUserName() + " empfangen");

        // Neuer Client moechte sich einloggen, Client in Client-Liste
        // eintragen
        if (!clients.existsClient(receivedPdu.getUserName())) {
            // Client noch nicht in Client-Liste und kann erstellt werden.
            newLog.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
            ClientListEntry client = new ClientListEntry(receivedPdu.getUserName(), connection);
            client.setLoginTime(System.nanoTime());
            // Client wird erstellt
            clients.createClient(receivedPdu.getUserName(), client);
            // Status des Clients wird auf REGISTERING gesetzt. Er befindet sich
            // in der Anmeldung
            clients.changeClientStatus(receivedPdu.getUserName(),
                    ClientConversationStatus.REGISTERING);
            newLog.debug("User " + receivedPdu.getUserName() + " nun in Clientliste");
            // Setzten von UserName auf den EventInitiator Client.
            userName = receivedPdu.getUserName();
            clientThreadName = receivedPdu.getClientThreadName();
            Thread.currentThread().setName(receivedPdu.getUserName());
            newLog.debug("Laenge der Clientliste: " + clients.size());
            serverGuiInterface.incrNumberOfLoggedInClients();

            // Login-Event an alle Clients (auch an den gerade aktuell
            // anfragenden) senden

            Vector<String> clientList = clients.getClientNameList();
            eventPDU = ChatPDU.createLoginEventPdu(userName, clientList, receivedPdu);
            sendLoginListUpdateEvent(eventPDU);

            // Login Response senden
            ChatPDU responsePdu = ChatPDU.createLoginResponsePdu(userName, receivedPdu);

            try {
                clients.getClient(userName).getConnection().send(responsePdu);
            } catch (Exception e) {
                newLog.debug("Senden einer Login-Response-PDU an " + userName + " fehlgeschlagen");
                newLog.debug("Exception Message: " + e.getMessage());
            }

            newLog.debug("Login-Response-PDU an Client " + userName + " gesendet");

            // Zustand des Clients aendern
            clients.changeClientStatus(userName, ClientConversationStatus.REGISTERED);

        } else {
            // User bereits angemeldet, Fehlermeldung an Client senden,
            // Fehlercode an Client senden
            eventPDU = ChatPDU.createLoginErrorResponsePdu(receivedPdu, ChatPDU.LOGIN_ERROR);

            try {
                connection.send(eventPDU);
                newLog.debug("Login-Response-PDU an " + receivedPdu.getUserName()
                        + " mit Fehlercode " + ChatPDU.LOGIN_ERROR + " gesendet");
            } catch (Exception e) {
                newLog.debug("Senden einer Login-Response-PDU an " + receivedPdu.getUserName()
                        + " nicth moeglich");
                ExceptionHandler.logExceptionAndTerminate(e);
            }
        }
    }

    @Override
    protected void logoutRequestAction(ChatPDU receivedPdu) {

        ChatPDU eventPDU;
        logoutCounter.getAndIncrement();
        newLog.debug("Logout-Request von " + receivedPdu.getUserName() + ", LogoutCount = "
                + logoutCounter.get());

        newLog.debug("Logout-Request-PDU von " + receivedPdu.getUserName() + " empfangen");

        if (!clients.existsClient(userName)) {
            newLog.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
        } else {

            // Event an Client versenden
            Vector<String> clientList = clients.getClientNameList();
            // Erstellen einer LOGOUT-EVENT-PDU mit den Daten des EventInitator
            // Clients.
            eventPDU = ChatPDU.createLogoutEventPdu(userName, clientList, receivedPdu);
            // Status des Clients wird auf UNREGISTERING gesetzt. Er möchte
            // sich abmelden.
            clients.changeClientStatus(receivedPdu.getUserName(),
                    ClientConversationStatus.UNREGISTERING);

            // Event an Clients senden
            sendLoginListUpdateEvent(eventPDU);
            serverGuiInterface.decrNumberOfLoggedInClients();

            // Der Thread muss hier noch warten, bevor ein Logout-Response gesendet
            // wird, da sich sonst ein Client abmeldet, bevor er seinen letzten Event
            // empfangen hat. das funktioniert nicht bei einer grossen Anzahl an
            // Clients (kalkulierte Events stimmen dann nicht mit tatsaechlich
            // empfangenen Events ueberein.
            // In der Advanced-Variante wird noch ein Confirm gesendet, das ist
            // sicherer.

            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                ExceptionHandler.logException(e);
            }

            clients.changeClientStatus(receivedPdu.getUserName(),
                    ClientConversationStatus.UNREGISTERED);

            // Logout Response senden
            sendLogoutResponse(receivedPdu.getUserName());

            // Worker-Thread des Clients, der den Logout-Request gesendet
            // hat, auch gleich zum Beenden markieren
            clients.finish(receivedPdu.getUserName());
            newLog.debug("Laenge der Clientliste beim Vormerken zum Loeschen von "
                    + receivedPdu.getUserName() + ": " + clients.size());
        }
    }

    @Override
    protected void chatMessageRequestAction(ChatPDU receivedPdu) {

        ClientListEntry client;
        clients.setRequestStartTime(receivedPdu.getUserName(), startTime);
        clients.incrNumberOfReceivedChatMessages(receivedPdu.getUserName());
        serverGuiInterface.incrNumberOfRequests();
        newLog.debug("Chat-Message-Request-PDU von " + receivedPdu.getUserName()
                + " mit Sequenznummer " + receivedPdu.getSequenceNumber() + " empfangen");

        if (!clients.existsClient(receivedPdu.getUserName())) {
            newLog.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
        } else {
            // Liste der betroffenen Clients ermitteln
            Vector<String> sendList = clients.getClientNameList();
            ChatPDU pdu = ChatPDU.createChatMessageEventPdu(userName, receivedPdu);

            // Event an Clients senden
            for (String s : new Vector<>(sendList)) {
                client = clients.getClient(s);
                try {
                    if ((client != null)
                            && (client.getStatus() != ClientConversationStatus.UNREGISTERED)) {
                        pdu.setUserName(client.getUserName());
                        client.getConnection().send(pdu);
                        newLog.debug("Chat-Event-PDU an " + client.getUserName() + " gesendet");
                        clients.incrNumberOfSentChatEvents(client.getUserName());
                        eventCounter.getAndIncrement();
                        newLog.debug(userName + ": EventCounter erhoeht = " + eventCounter.get()
                                + ", Aktueller ConfirmCounter = " + confirmCounter.get()
                                + ", Anzahl gesendeter ChatMessages von dem Client = "
                                + receivedPdu.getSequenceNumber());
                    }
                } catch (Exception e) {
                    newLog.debug("Senden einer Chat-Event-PDU an " + client.getUserName()
                            + " nicht moeglich");
                    ExceptionHandler.logException(e);
                }
            }

            client = clients.getClient(receivedPdu.getUserName());
            if (client != null) {
                ChatPDU responsePdu = ChatPDU.createChatMessageResponsePdu(
                        receivedPdu.getUserName(), 0, 0, 0, 0,
                        client.getNumberOfReceivedChatMessages(), receivedPdu.getClientThreadName(),
                        (System.nanoTime() - client.getStartTime()));

                if (responsePdu.getServerTime() / 1000000 > 100) {
                    newLog.debug(Thread.currentThread().getName()
                            + ", Benoetigte Serverzeit vor dem Senden der Response-Nachricht > 100 ms: "
                            + responsePdu.getServerTime() + " ns = "
                            + responsePdu.getServerTime() / 1000000 + " ms");
                }

                try {
                    client.getConnection().send(responsePdu);
                    newLog.debug(
                            "Chat-Message-Response-PDU an " + receivedPdu.getUserName() + " gesendet");
                } catch (Exception e) {
                    newLog.debug("Senden einer Chat-Message-Response-PDU an " + client.getUserName()
                            + " nicht moeglich");
                    ExceptionHandler.logExceptionAndTerminate(e);
                }
            }
            newLog.debug("Aktuelle Laenge der Clientliste: " + clients.size());
        }
    }

    /**
     * Verbindung zu einem Client ordentlich abbauen
     */
    private void closeConnection() {

        newLog.debug("Schliessen der Chat-Connection zum " + userName);

        // Bereinigen der Clientliste falls erforderlich

        if (clients.existsClient(userName)) {
            newLog.debug("Close Connection fuer " + userName
                    + ", Laenge der Clientliste vor dem bedingungslosen Loeschen: "
                    + clients.size());

            clients.deleteClientWithoutCondition(userName);
            newLog.debug("Laenge der Clientliste nach dem bedingungslosen Loeschen von " + userName
                    + ": " + clients.size());
        }

        try {
            connection.close();
        } catch (Exception e) {
            newLog.debug("Exception bei close");
            // ExceptionHandler.logException(e);
        }
    }

    /**
     * Antwort-PDU fuer den initiierenden Client aufbauen und senden
     *
     * @param eventInitiatorClient Name des Clients
     */
    private void sendLogoutResponse(String eventInitiatorClient) {

        ClientListEntry client = clients.getClient(eventInitiatorClient);

        if (client != null) {
            ChatPDU responsePdu = ChatPDU.createLogoutResponsePdu(eventInitiatorClient, 0, 0, 0,
                    0, client.getNumberOfReceivedChatMessages(), clientThreadName);

            newLog.debug(eventInitiatorClient + ": SentEvents aus Clientliste: "
                    + client.getNumberOfSentEvents() + ": ReceivedConfirms aus Clientliste: "
                    + client.getNumberOfReceivedEventConfirms());
            try {
                clients.getClient(eventInitiatorClient).getConnection().send(responsePdu);
            } catch (Exception e) {
                newLog.debug("Senden einer Logout-Response-PDU an " + eventInitiatorClient
                        + " fehlgeschlagen");
                newLog.debug("Exception Message: " + e.getMessage());
            }

            newLog.debug("Logout-Response-PDU an Client " + eventInitiatorClient + " gesendet");
        }
    }

    /**
     * Prueft, ob Clients aus der Clientliste geloescht werden koennen
     *
     * @return boolean, true: Client geloescht, false: Client nicht geloescht
     */
    private boolean checkIfClientIsDeletable() {

        ClientListEntry client;

        // Worker-Thread beenden, wenn sein Client schon abgemeldet ist
        if (userName != null) {
            client = clients.getClient(userName);
            if (client != null) {
                if (client.isFinished()) {
                    // Loesche den Client aus der Clientliste
                    // Ein Loeschen ist aber nur zulaessig, wenn der Client
                    // nicht mehr in einer anderen Warteliste ist
                    newLog.debug("Laenge der Clientliste vor dem Entfernen von " + userName + ": "
                            + clients.size());
                    if (clients.deleteClient(userName)) {
                        // Jetzt kann auch Worker-Thread beendet werden

                        newLog.debug("Laenge der Clientliste nach dem Entfernen von " + userName + ": "
                                + clients.size());
                        newLog.debug("Worker-Thread fuer " + userName + " zum Beenden vorgemerkt");
                        return true;
                    }
                }
            }
        }

        // Garbage Collection in der Clientliste durchfuehren
        Vector<String> deletedClients = clients.gcClientList();
        if (deletedClients.contains(userName)) {
            newLog.debug("Ueber Garbage Collector ermittelt: Laufender Worker-Thread fuer "
                    + userName + " kann beendet werden");
            finished = true;
            return true;
        }
        return false;
    }

    @Override
    protected void handleIncomingMessage() {
        if (checkIfClientIsDeletable()) {
            return;
        }

        // Warten auf naechste Nachricht
        ChatPDU receivedPdu;

        // Nach einer Minute wird geprueft, ob Client noch eingeloggt ist
        final int RECEIVE_TIMEOUT = 1200000;

        try {
            receivedPdu = (ChatPDU) connection.receive(RECEIVE_TIMEOUT);

            // Nachricht empfangen
            // Zeitmessung fuer Serverbearbeitungszeit starten
            startTime = System.nanoTime();

        } catch (ConnectionTimeoutException e) {

            // Wartezeit beim Empfang abgelaufen, pruefen, ob der Client
            // ueberhaupt noch etwas sendet
            newLog.debug(
                    "Timeout beim Empfangen, " + RECEIVE_TIMEOUT + " ms ohne Nachricht vom Client");

            if (clients.getClient(userName) != null) {
                if (clients.getClient(userName)
                        .getStatus() == ClientConversationStatus.UNREGISTERING) {
                    // Worker-Thread wartet auf eine Nachricht vom Client, aber es
                    // kommt nichts mehr an
                    newLog.error(
                            "Client ist im Zustand UNREGISTERING und bekommt aber keine Nachricht mehr");
                    // Zur Sicherheit eine Logout-Response-PDU an Client senden und
                    // dann Worker-Thread beenden
                    finished = true;
                }
            }
            return;

        } catch (EndOfFileException e) {
            newLog.debug("End of File beim Empfang, vermutlich Verbindungsabbau des Partners fuer "
                    + userName);
            finished = true;
            return;

        } catch (java.net.SocketException e) {
            newLog.error("Verbindungsabbruch beim Empfang der naechsten Nachricht vom Client "
                    + getName());
            finished = true;
            return;

        } catch (Exception e) {
            newLog.error(
                    "Empfang einer Nachricht fehlgeschlagen, Workerthread fuer User: " + userName);
            ExceptionHandler.logException(e);
            finished = true;
            return;
        }

        // Empfangene Nachricht bearbeiten
        try {

            switch (receivedPdu.getPduType()) {
                // Login-Request vom Client empfangen
                case LOGIN_REQUEST -> {
                    loginRequestAction(receivedPdu);
                    if (auditLogServerEnabled) {
                        // AuditLog-Satz erzeugen und senden
                        try {
                            auditLogConnection.send(receivedPdu, AuditLogPduType.LOGIN_REQUEST);
                        } catch (Exception e) {
                            ExceptionHandler.logException(e);
                        }
                    }
                }
                // Chat-Nachricht angekommen, an alle verteilen
                case CHAT_MESSAGE_REQUEST -> {
                    chatMessageRequestAction(receivedPdu);
                    if (auditLogServerEnabled) {
                        // AuditLog-Satz erzeugen und senden
                        try {
                            auditLogConnection.send(receivedPdu, AuditLogPduType.CHAT_MESSAGE_REQUEST);
                        } catch (Exception e) {
                            ExceptionHandler.logException(e);
                        }
                    }
                }
                // Logout-Request vom Client empfangen
                case LOGOUT_REQUEST -> {
                    logoutRequestAction(receivedPdu);
                    if (auditLogServerEnabled) {
                        // AuditLog-Satz erzeugen und senden
                        try {
                            auditLogConnection.send(receivedPdu, AuditLogPduType.LOGOUT_REQUEST);
                        } catch (Exception e) {
                            ExceptionHandler.logException(e);
                        }
                    }
                }
                default -> newLog.debug("Falsche PDU empfangen von Client: " + receivedPdu.getUserName()
                        + ", PduType: " + receivedPdu.getPduType());
            }
        } catch (Exception e) {
            newLog.error("Exception bei der Nachrichtenverarbeitung");
            ExceptionHandler.logExceptionAndTerminate(e);
        }
    }
}
