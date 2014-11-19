/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import java.util.ArrayList;
import javax.json.JsonArray;
import javax.json.JsonObject;
import org.slf4j.LoggerFactory;

/**
 *
 * @author martinhudec
 */
public class Users {

    ArrayList<UserDevice> usersList = new ArrayList<>();
    JsonArray jsonArray;
    private final org.slf4j.Logger logger;
    
    public Users(JsonArray ja) {
        this.jsonArray = ja;
        this.logger = LoggerFactory.getLogger(CallSession.class);
        fillUserList();
    //    usersList.add(new UserDevice("phone", "heslo", 101));
     //   usersList.add(new UserDevice("ntb", "heslo", 100));
      //  usersList.add(new UserDevice("banan", "banan", 102));
    }

    public void fillUserList() {
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject object = jsonArray.getJsonObject(i);
            usersList.add(new UserDevice(object.getString("userName"), object.getString("password"), Integer.parseInt(object.getString("extension"))));
           
        }
    }

    public ArrayList<UserDevice> getUsersList() {
        return usersList;
    }

}
