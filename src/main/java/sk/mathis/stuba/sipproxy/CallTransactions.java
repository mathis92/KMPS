/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy;

import java.util.ArrayList;
import javax.sip.RequestEvent;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.swing.table.DefaultTableModel;
import org.apache.log4j.spi.LoggerFactory;
import org.slf4j.Logger;
import sk.mathis.stuba.sipproxy.equip.CallSession;
import sk.mathis.stuba.sipproxy.equip.Server;

/**
 *
 * @author martinhudec
 */
public class CallTransactions {

    private ArrayList<TransactionMessages> transactionMessagesPanelList = new ArrayList<>();
    private ArrayList<TransactionMessage> transactionMessageList = new ArrayList<>();
    private AppGui gui;
    private final Server sipServer;
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(CallTransactions.class);
    private CallSession session;
    private TransactionTabPanel tabPanel = new TransactionTabPanel();

    public CallTransactions(Server sipServer, AppGui gui, CallSession session) {
        this.sipServer = sipServer;
        this.session = session;
    }

    public boolean solveActionSide(Request request) {
        ViaHeader vh = (ViaHeader) request.getHeader(ViaHeader.NAME);

        logger.debug("vypis hosta" + vh.getReceived());

        if (!vh.getHost().equals(sipServer.getSipDomain())) {
            logger.debug("prijaty");
            return true;
        } else {
            logger.debug("odoslany");
            return false;
        }
    }
    public boolean solveActionSide(Response response) {
        ViaHeader vh = (ViaHeader) response.getHeader(ViaHeader.NAME);

        logger.debug("vypis hosta" + vh.getReceived());

        if (vh.getHost().equals(sipServer.getSipDomain())) {
            logger.debug("prijaty");
            return true;
        } else {
            logger.debug("odoslany");
            return false;
        }
    }

    public void writeTransactionMessages() {
        int i = 0;
        //     logger.debug("trmes " + transactionMessageList.size());
        for (TransactionMessage trmes : transactionMessageList) {
            //    logger.debug("icko " + i + "transactionlist size " + transactionMessageList.size());
            writeTransactionStats(transactionMessagesPanelList.get(i), trmes.getSession());
            while (!trmes.getQueue().isEmpty()) {
                Object obj = trmes.getQueue().poll();
                if(obj instanceof Request){
                    if(solveActionSide((Request)obj)){
                         transactionMessagesPanelList.get(i).getMessageArea().append("REQUEST RECEIVED\n");
                    }else { 
                         transactionMessagesPanelList.get(i).getMessageArea().append("REQUEST SENT\n");
                    }
                }else if(obj instanceof Response){
                    if(solveActionSide((Response)obj)){
                        transactionMessagesPanelList.get(i).getMessageArea().append("RESPONSE RECEIVED\n");
                    }else { 
                        transactionMessagesPanelList.get(i).getMessageArea().append("RESPONSE SENT\n");
                    }
                }
                transactionMessagesPanelList.get(i).getMessageArea().append(obj.toString() + "\n\n");
                transactionMessagesPanelList.get(i).getMessageArea().append("----------------------------------------------------------------------------------------------------------------------\n");
            }
            i++;
        }

    }

    public void writeTransactionStats(TransactionMessages tm, CallSession session) {

        Object[] data = new Object[9];
        DefaultTableModel callsTablemodel;
        callsTablemodel = (DefaultTableModel) tm.getCallsTable().getModel();

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
        tm.getCallsTable().setModel(callsTablemodel);
    }

    public void writeTransactionsToList() {
        if (sipServer.getCallSessionList() != null) {
            if (transactionMessagesPanelList.isEmpty()) {
                int i = 0;
                for (TransactionMessage trmes : transactionMessageList) {
                    i++;
                    TransactionMessages transactionPanel = new TransactionMessages();
                    tabPanel.getTransactionTabPane().addTab("tr n." + i, transactionPanel);
                    transactionMessagesPanelList.add(transactionPanel);
                    writeTransactionStats(transactionPanel, trmes.getSession());
                }
            } else if (transactionMessageList.size() > transactionMessagesPanelList.size()) {
                for (int i = transactionMessagesPanelList.size(); i < transactionMessageList.size(); i++) {
                    logger.debug("icko " + i);
                    TransactionMessages transactionPanel = new TransactionMessages();
                    tabPanel.getTransactionTabPane().addTab("tr n." + (i + 1), transactionPanel);
                    transactionMessagesPanelList.add(transactionPanel);
                    writeTransactionStats(transactionPanel, transactionMessageList.get(i).getSession());
                }
            }
        }

    }

    public void addTransactionsToList() {
        if (sipServer.getCallSessionList() != null) {
            Request request;
            Response response;
            ViaHeader via;
            CSeqHeader cseq;
            while (!session.getCallTransactionBuffer().isEmpty()) {
                Object object = session.getCallTransactionBuffer().poll();
                if (object instanceof Request) {
                    //           logger.debug("je to request");
                    request = (Request) object;
                    solveActionSide(request);

                    via = (ViaHeader) request.getHeader(ViaHeader.NAME);
                    cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);

                    if (transactionMessageList.isEmpty()) {
                        //          logger.debug("pridavam do messageListu je empty");
                        TransactionMessage trMes = new TransactionMessage(request, cseq.getSeqNumber(), via.getBranch(), session);
                        transactionMessageList.add(trMes);
                    } else {
                        int found = 0;
                        //            logger.debug("pridavam do messageListu vyhladavam v liste velkost je " + transactionMessageList.size());
                        for (TransactionMessage trmes : transactionMessageList) {
                            if (trmes.getBranch().equals(via.getBranch()) && cseq.getSeqNumber() == trmes.getCseq()) {
                                trmes.queue.add(request);
                                found = 1;

                            }
                        }
                        if (found == 0) {
                            //              logger.debug("v liste som nic nenasiel pridavam nakoniec");
                            transactionMessageList.add(new TransactionMessage(request, cseq.getSeqNumber(), via.getBranch(), session));
                        }
                    }

                } else if (object instanceof Response) {
                    //    logger.debug("je to response");
                    response = (Response) object;
                    via = (ViaHeader) response.getHeader(ViaHeader.NAME);
                    cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

                    if (transactionMessageList.isEmpty()) {
                        //     logger.debug("pridavam do messageListu je empty");
                        TransactionMessage trMes = new TransactionMessage(response, cseq.getSeqNumber(), via.getBranch(), session);
                        transactionMessageList.add(trMes);
                    } else {
                        int found = 0;
                        //              logger.debug("pridavam do messageListu vyhladavam v liste velkost je " + transactionMessageList.size());
                        for (TransactionMessage trmes : transactionMessageList) {
                            if (trmes.getBranch().equals(via.getBranch()) && trmes.getCseq() == cseq.getSeqNumber()) {
                                trmes.queue.add(response);
                                found = 1;

                            }
                        }
                        if (found == 0) {
                            //                logger.debug("v liste som nic nenasiel pridavam nakoniec");
                            transactionMessageList.add(new TransactionMessage(response, cseq.getSeqNumber(), via.getBranch(), session));
                        }
                    }
                }
            }
        }
    }

    public CallSession getSession() {
        return session;
    }

    public void setSession(CallSession session) {
        this.session = session;
    }

    public AppGui getGui() {
        return gui;
    }

    public Logger getLogger() {
        return logger;
    }

    public Server getSipServer() {
        return sipServer;
    }

    public ArrayList<TransactionMessage> getTransactionMessageList() {
        return transactionMessageList;
    }

    public ArrayList<TransactionMessages> getTransactionMessagesPanelList() {
        return transactionMessagesPanelList;
    }

    public TransactionTabPanel getTabPanel() {
        return tabPanel;
    }

}
