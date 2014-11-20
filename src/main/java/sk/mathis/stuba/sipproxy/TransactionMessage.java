/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.servlet.sip.SipSession;
import javax.sip.message.Request;
import javax.sip.message.Response;
import sk.mathis.stuba.sipproxy.equip.CallSession;

/**
 *
 * @author martinhudec
 */
public class TransactionMessage {

    Queue<Object> queue = new ConcurrentLinkedDeque<>();
    long cseq;
    String branch;
    CallSession session;

    public TransactionMessage(Response resp, long cseq, String branch, CallSession session) {
        queue.add(resp);
        this.cseq = cseq;
        this.branch = branch;
        this.session = session;
    }

    public TransactionMessage(Request req, long cseq, String branch, CallSession session) {
        queue.add(req);
        this.cseq = cseq;
        this.branch = branch;
        this.session = session;
    }

    public String getBranch() {
        return branch;
    }

    public Queue<Object> getQueue() {
        return queue;
    }

   

    public long getCseq() {
        return cseq;
    }

    public CallSession getSession() {
        return session;
    }

}
