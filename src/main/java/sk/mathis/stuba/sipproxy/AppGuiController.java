/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import org.slf4j.LoggerFactory;
import sk.mathis.stuba.sipproxy.equip.CallSession;
import sk.mathis.stuba.sipproxy.equip.Registration;
import sk.mathis.stuba.sipproxy.equip.Server;
import sk.mathis.stuba.sipproxy.equip.UserDevice;

/**
 *
 * @author martinhudec
 */
public class AppGuiController implements Runnable {

    AppGui gui;
    Server sipServer;
    Integer callCount = 0;
    Integer transactionCount = 0;
    private final org.slf4j.Logger logger;
    
    private ArrayList<CallMessages> callmessagesPanelList = new ArrayList<>();
    private ArrayList<TransactionMessages> transactionMessagesPanelList = new ArrayList<>();
    private ArrayList<TransactionMessage> transactionMessageList = new ArrayList<>();

    private ArrayList<CallTransactions> callTransactionsList = new ArrayList<>();
    private ArrayList<TransactionTabPanel> transactionTabPanelList = new ArrayList<>();
    private int save = 0;
    // private ArrayList<ArrayList<TransactionMessage>> callmessagesTrList = new ArrayList<>();
    public AppGuiController(AppGui gui, Server srvr) {
        this.gui = gui;
        this.sipServer = srvr;
        this.logger = LoggerFactory.getLogger(AppGuiController.class);

    }

    @Override
    public void run() {
        logger.debug("zopinam appguicontroller");
        while (true) {
            updadateRegTable();
            updateStatsTable();
            updateCallsTable();
            addSessionsTolist();
            writeCallMessages();
            writeRegistrationLog();
            writeCallSessionLog();
            //addTransactionsToList();
            //writeTransactionsToList();
            //writeTransactionMessages();
            writeCallTransactionToList();
            addToCallTransactionList();
            fillUserTable();

            try {

                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Logger.getLogger(AppGuiController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public void saveToFile( JTextArea ta) {
        if (!sipServer.getCallSessionList().isEmpty()) {
            PrintWriter pw = null;
            try {
                String filename = "file" + save ;
                save++;
                File file = new File("/Users/martinhudec/Desktop/calls" + filename + ".txt");
                pw = new PrintWriter(file, "UTF-8");
                pw.println(ta.getText());

            } catch (FileNotFoundException ex) {
                Logger.getLogger(AppGuiController.class.getName()).log(Level.SEVERE, null, ex);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(AppGuiController.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                pw.close();
            }
        }
    }

    public void writeRegistrationLog() {
        if (sipServer.getRegistrationList() != null) {
            for (Registration reg : sipServer.getRegistrationList()) {
                if (!reg.getRegistrationMessages().isEmpty()) {
                    gui.getLogArea().append(reg.getRegistrationMessages().poll());
                    gui.getLogArea().append("----------------------------------------------------------------------------------------------------------------------\n");
                }
            }
        }
    }

    public void writeCallSessionLog() {
        if (sipServer.getCallSessionList() != null) {
            // System.out.println("call session list nieje prazdny " + sipServer.getCallSessionList().size());
            for (CallSession session : sipServer.getCallSessionList()) {
                if (!session.getCallSessionMessagesLog().isEmpty()) {
                    //logger.debug("vytahujem session z buffera a zapisujem " );
                    gui.getLogArea().append(session.getCallSessionMessagesLog().poll());
                    gui.getLogArea().append("----------------------------------------------------------------------------------------------------------------------\n");
                }
            }
        }
    }

    public void fillUserTable() {
        Object[] data = new Object[6];
        DefaultTableModel registrationTablemodel;
        registrationTablemodel = (DefaultTableModel) gui.getNotRegisteredTable().getModel();
        registrationTablemodel.setRowCount(0);

        int i = 0;

        for (UserDevice dev : sipServer.getUsers().getUsersList()) {
            if (!dev.getRegistered()) {
                i++;
                data[0] = i;
                data[1] = dev.getName();
                data[2] = dev.getExtension();
                data[3] = "not set";
                data[4] = "not set";
                data[5] = "not set";

                registrationTablemodel.addRow(data);
            }
        }
        gui.getNotRegisteredTable().setModel(registrationTablemodel);

    }

    public JsonArray readUsers() throws FileNotFoundException {
        JsonReaderFactory jrf = Json.createReaderFactory(null);
        FileInputStream fis = new FileInputStream(new File("/Users/martinhudec/Desktop/users.txt"));
        try (JsonReader jr = jrf.createReader(fis, Charset.defaultCharset())) {
            JsonObject jo = jr.readObject();
            JsonArray ja = jo.getJsonArray("users");
            return ja;
        }
    }

    public void removeExtension(Object meno) {
        FileOutputStream fos = null;
        logger.debug("idem vymazavat meno " + meno);
        try {
            JsonObjectBuilder extObject = Json.createObjectBuilder();
            JsonArrayBuilder arrObject = Json.createArrayBuilder();
            JsonArray ja = readUsers();

            fos = new FileOutputStream("/Users/martinhudec/Desktop/users.txt");
            Map<String, Object> jwfConfig = new HashMap<>();
            jwfConfig.put(JsonGenerator.PRETTY_PRINTING, true);
            JsonWriterFactory jwf = Json.createWriterFactory(jwfConfig);
            try (JsonWriter writer = jwf.createWriter(fos)) {
                for (JsonValue jv : ja) {
                    JsonObject jo = (JsonObject) jv;
                    JsonObjectBuilder job = null;
                    if (!jo.getString("extension").equals(meno)) {
                        logger.debug("pridal som " + jo.getString("extension"));
                        job = Json.createObjectBuilder();
                        job.add("userName", jo.getString("userName"));
                        job.add("password", jo.getString("password"));
                        job.add("extension", jo.getString("extension"));

                    }
                    if (job != null) {
                        arrObject.add(job);
                    }
                }

                JsonObjectBuilder jo = Json.createObjectBuilder();
                jo.add("users", arrObject);
                writer.writeObject(jo.build());
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Server.class
                    .getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();

            } catch (IOException ex) {
                Logger.getLogger(Server.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void addExtension(String name, String extension, String password) {
        FileOutputStream fos = null;

        try {
            JsonObjectBuilder extObject = Json.createObjectBuilder();
            JsonArrayBuilder arrObject = Json.createArrayBuilder();
            JsonArray ja = readUsers();

            fos = new FileOutputStream("/Users/martinhudec/Desktop/users.txt");
            Map<String, Object> jwfConfig = new HashMap<>();
            jwfConfig.put(JsonGenerator.PRETTY_PRINTING, true);
            JsonWriterFactory jwf = Json.createWriterFactory(jwfConfig);
            try (JsonWriter writer = jwf.createWriter(fos)) {
                for (JsonValue jv : ja) {
                    JsonObject jo = (JsonObject) jv;
                    JsonObjectBuilder job = Json.createObjectBuilder();
                    for (Map.Entry<String, JsonValue> entry : jo.entrySet()) {
                        job.add(entry.getKey(), entry.getValue());
                    }
                    arrObject.add(job);
                }

                extObject.add("userName", name);
                extObject.add("password", password);
                extObject.add("extension", extension);
                arrObject.add(extObject);

                JsonObjectBuilder jo = Json.createObjectBuilder();
                jo.add("users", arrObject);
                writer.writeObject(jo.build());
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Server.class
                    .getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();

            } catch (IOException ex) {
                Logger.getLogger(Server.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void writeCallTransactionToList() {
        if (sipServer.getCallSessionList() != null) {
            for (CallSession session : sipServer.getCallSessionList()) {
                //      logger.debug("mam session");
                if (transactionTabPanelList.isEmpty()) {
                    //          logger.debug("tabpanellist je prazdny");
                    int i = 0;
                    for (CallTransactions transaction : callTransactionsList) {
                        //             logger.debug("vytvaram call transactions");
                        i++;
                        TransactionTabPanel tabPanel = transaction.getTabPanel();
                        gui.getCallTrTabPane().addTab("call n." + i, tabPanel);
                        transactionTabPanelList.add(tabPanel);
                    }
                } else if (callTransactionsList.size() > transactionTabPanelList.size()) {
                    //            logger.debug("treba pridat panely ");
                    for (int i = transactionTabPanelList.size(); i < callTransactionsList.size(); i++) {
                        //                logger.debug("pridavam panely");
                        TransactionTabPanel tabPanel = callTransactionsList.get(i).getTabPanel();
                        gui.getCallTrTabPane().addTab("call n." + (i + 1), tabPanel);
                        transactionTabPanelList.add(tabPanel);
                    }
                }
            }
        }
    }

    public void addToCallTransactionList() {
        if (sipServer.getCallSessionList() != null) {

            for (CallSession session : sipServer.getCallSessionList()) {
                int found = 0;
                for (CallTransactions ctrans : callTransactionsList) {
                    if (session.getCallIdHeader().equals(ctrans.getSession().getCallIdHeader())) {
                        ctrans.setSession(session);
                        ctrans.addTransactionsToList();
                        ctrans.writeTransactionsToList();
                        ctrans.writeTransactionMessages();
                        found = 1;
                    }
                }
                if (found == 0) {
                    CallTransactions ctrans = new CallTransactions(sipServer, gui, session);
                    ctrans.addTransactionsToList();
                    ctrans.writeTransactionsToList();
                    ctrans.writeTransactionMessages();
                    callTransactionsList.add(ctrans);
                }
            }
        }
    }

    public void addSessionsTolist() {
        if (sipServer.getCallSessionList() != null) {
            if (callmessagesPanelList.isEmpty()) {
                for (CallSession session : sipServer.getCallSessionList()) {
                    callCount++;
                    CallMessages messagesPanel = new CallMessages(this);
                    gui.getCallTabPane().addTab("call n." + callCount, messagesPanel);
                    callmessagesPanelList.add(messagesPanel);
                    writeCallStats(messagesPanel, session);
                }
            }
            if (sipServer.getCallSessionList().size() > callmessagesPanelList.size()) {
                for (int i = callmessagesPanelList.size(); i < sipServer.getCallSessionList().size(); i++) {
                    CallMessages messagesPanel = new CallMessages(this);
                    gui.getCallTabPane().addTab("call n." + (i + 1), messagesPanel);
                    callmessagesPanelList.add(messagesPanel);
                    writeCallStats(messagesPanel, sipServer.getCallSessionList().get(i));
                }
            }
        }
    }

    public void writeCallMessages() {
        if (sipServer.getCallSessionList() != null) {
            int i = 0;

            for (CallSession session : sipServer.getCallSessionList()) {
                //      logger.debug("icko " + i + "sessionlist size " + sipServer.getCallSessionList().size());
                writeCallStats(callmessagesPanelList.get(i), session);
                while (!session.getCallSessionMessagesBuffer().isEmpty()) {
                    callmessagesPanelList.get(i).getMessageArea().append(session.getCallSessionMessagesBuffer().poll() + "\n\n");
                    callmessagesPanelList.get(i).getMessageArea().append("----------------------------------------------------------------------------------------------------------------------\n");
                }
                i++;
            }
        }
    }

    public void writeCallStats(CallMessages cm, CallSession session) {
        if (session.getCalleeReg() != null) {
            Object[] data = new Object[9];
            DefaultTableModel callsTablemodel;
            callsTablemodel = (DefaultTableModel) cm.getCallsTable().getModel();

            callsTablemodel.setRowCount(0);
            data[0] = 1;
            data[1] = session.getCallerReg().getDev().getName();
            String tmp = session.getCallerReg().getRegHost() + "/" + session.getCallerReg().getRegPort();
            data[2] = tmp;
            data[3] = session.getCallerReg().getDev().getExtension();
            data[4] = session.getCalleeReg().getDev().getName();
            tmp = session.getCalleeReg().getRegHost() + "/" + session.getCalleeReg().getRegPort();
            data[5] = tmp;
            data[6] = session.getCalleeReg().getDev().getExtension();
            data[7] = session.computeDuration();
            data[8] = session.getState();
            callsTablemodel.addRow(data);
            cm.getCallsTable().setModel(callsTablemodel);
        }
    }

    public void updateCallsTable() {
        if (sipServer.getCallSessionList() != null) {

            Object[] data = new Object[9];
            DefaultTableModel callsTablemodel;
            callsTablemodel = (DefaultTableModel) gui.getCallsTable().getModel();

            callsTablemodel.setRowCount(0);
            //logger.debug("idem vytvarat tabulku");
            int i = 0;
            // logger.debug(sipServer.getCallSessionList().size() + "velkost listu");

            for (int j = sipServer.getCallSessionList().size(); j > 0; j--) {
                if (sipServer.getCallSessionList().get(j - 1).getCalleeReg() != null) {
                    if (sipServer.getCallSessionList().
                            get(j - 1).
                            getCalleeReg().
                            getDev() != null) {
                        i++;
                        data[0] = i;
                        data[1] = sipServer.getCallSessionList().get(j - 1).getCallerReg().getDev().getName();
                        String tmp = sipServer.getCallSessionList().get(j - 1).getCallerReg().getRegHost() + "/" + sipServer.getCallSessionList().get(j - 1).getCallerReg().getRegPort();
                        data[2] = tmp;
                        data[3] = sipServer.getCallSessionList().get(j - 1).getCallerReg().getDev().getExtension();
                        data[4] = sipServer.getCallSessionList().get(j - 1).getCalleeReg().getDev().getName();
                        tmp = sipServer.getCallSessionList().get(j - 1).getCalleeReg().getRegHost() + "/" + sipServer.getCallSessionList().get(j - 1).getCalleeReg().getRegPort();
                        data[5] = tmp;
                        data[6] = sipServer.getCallSessionList().get(j - 1).getCalleeReg().getDev().getExtension();

                        data[7] = sipServer.getCallSessionList().get(j - 1).computeDuration();
                        data[8] = sipServer.getCallSessionList().get(j - 1).getState();
                        callsTablemodel.addRow(data);
                        //x       logger.debug("pridal som riadok ");
                    }
                }
            }
            gui.getCallsTable().setModel(callsTablemodel);
        }
    }

    public void updateStatsTable() {
        if (sipServer.getCallSessionList() != null) {
            Object[] data = new Object[3];
            DefaultTableModel statsTablemodel;
            statsTablemodel = (DefaultTableModel) gui.getStatTable().getModel();
            statsTablemodel.setRowCount(0);
            int j = 0;
            for (Registration reg : sipServer.getRegistrationList()) {
                if (!reg.getState().equals("UNREGISTERED")) {
                    j++;
                }
            }
            // logger.debug("pocet registracii " + j);
            data[0] = j;

            int i = 0;
            for (CallSession ses : sipServer.getCallSessionList()) {
                if (ses.getCalleeReg() != null) {
                    if (!ses.getState().equals("END")) {
                        i++;
                    }
                }
            }
            //logger.debug("pocet hovorov " + i);
            data[1] = i;

            data[2] = (sipServer.getCallSessionList().size() - i);

            statsTablemodel.addRow(data);
            gui.getStatTable().setModel(statsTablemodel);
        }
    }

    public void updadateRegTable() {
        if (sipServer.getRegistrationList() != null) {
            Object[] data = new Object[6];
            DefaultTableModel registrationTablemodel;
            registrationTablemodel = (DefaultTableModel) gui.getRegistrationTable().getModel();
            registrationTablemodel.setRowCount(0);
            int i = 0;

            for (Registration temp : sipServer.getRegistrationList()) {
                if (!temp.getState().equals("UNREGISTERED")) {
                    if (temp.getDev() == null) {
                        break;
                    }
                    i++;
                    data[0] = i;
                    data[1] = (temp.getDev() == null) ? null : temp.getDev().getName();
                    data[2] = temp.getDev().getExtension();
                    data[3] = temp.getDev().getPort();
                    data[4] = temp.getRegHost();
                    data[5] = temp.getState();

                    registrationTablemodel.addRow(data);

                }
            }
            gui.getRegistrationTable().setModel(registrationTablemodel);

        }
    }
}
