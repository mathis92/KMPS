/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.json.JsonArray;
import javax.json.JsonObject;
import org.slf4j.LoggerFactory;

/**
 *
 * @author martinhudec
 */
public class Users {

    CopyOnWriteArrayList<UserDevice> usersList = new CopyOnWriteArrayList<>();
    JsonArray jsonArray;
    private final org.slf4j.Logger logger;

    public Users(JsonArray ja) {
        this.jsonArray = ja;
        this.logger = LoggerFactory.getLogger(CallSession.class);
        fillUserList();
    
    }

    public void fillUserList() {
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject object = jsonArray.getJsonObject(i);
            usersList.add(
                    new UserDevice(
                            object.getString("userName"),
                            object.getString("password"),
                            Integer.parseInt(object.getString("extension"))));
        }
    }

    public CopyOnWriteArrayList<UserDevice> getUsersList() {
        return usersList;
    }

    

}
