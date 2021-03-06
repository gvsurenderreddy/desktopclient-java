/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.model.chat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.model.Contact;
import org.kontalk.persistence.Database;

/**
 * A contact association with a chat.
 * Single chats have exactly one, group chats can have any number of members.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Member {
    private static final Logger LOGGER = Logger.getLogger(Member.class.getName());

    /**
     * Long-live authorization model of member in group.
     * Called 'Affiliation' in MUC
     * Do not modify, only add! Ordinal used in database
     */
    public enum Role {DEFAULT, OWNER, ADMIN};

    public static final String TABLE = "receiver";
    public static final String COL_CONTACT_ID = "user_id";
    public static final String COL_ROLE = "role";
    public static final String COL_CHAT_ID = "thread_id";
    public static final String SCHEMA = "(" +
            Database.SQL_ID +
            COL_CHAT_ID + " INTEGER NOT NULL, " +
            COL_CONTACT_ID + " INTEGER NOT NULL, " +
            COL_ROLE + " INTEGER NOT NULL, " +
            "UNIQUE (" + COL_CHAT_ID + ", " + COL_CONTACT_ID + "), " +
            "FOREIGN KEY (" + COL_CHAT_ID + ") REFERENCES " + Chat.TABLE + " (_id), " +
            "FOREIGN KEY (" + COL_CONTACT_ID + ") REFERENCES " + Contact.TABLE + " (_id) " +
            ")";

    private final Contact mContact;
    private final Role mRole;

    private int mID;

    private ChatState mState = ChatState.gone;
    // note: the Android client does not set active states when only viewing
    // the chat (not necessary according to XEP-0085), this makes the
    // extra date field a bit useless
    // TODO save last active date to DB
    private Date mLastActive = null;

    public Member(Contact contact){
        this(contact, Role.DEFAULT);
    }

    public Member(Contact contact, Role role) {
        this(0, contact, role);
    }

    private Member(int id, Contact contact, Role role) {
        mID = id;
        mContact = contact;
        mRole = role;
    }

    public Contact getContact() {
        return mContact;
    }

    public Role getRole() {
        return mRole;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Member))
            return false;

        return mContact.equals(((Member) o).mContact);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(mContact);
        return hash;
    }

    @Override
    public String toString() {
        return "Mem:cont={"+mContact+"},role="+mRole;
    }

    public ChatState getState() {
        return mState;
    }

    boolean insert(Database db, int chatID) {
        if (mID > 0) {
            LOGGER.warning("already in database");
            return true;
        }

        List<Object> recValues = Arrays.asList(
                chatID,
                getContact().getID(),
                mRole);
        mID = db.execInsert(TABLE, recValues);
        if (mID <= 0) {
            LOGGER.warning("could not insert member");
            return false;
        }
        return true;
    }

    void save(Database db) {
        // TODO
    }

    boolean delete(Database db) {
        if (mID <= 0) {
            LOGGER.warning("not in database");
            return true;
        }

        return db.execDelete(TABLE, mID);
    }

    protected void setState(ChatState state) {
        mState = state;
        if (mState == ChatState.active || mState == ChatState.composing)
            mLastActive = new Date();
    }

    /** Load Members of a chat. */
    static List<Member> load(Database db, int chatID, Map<Integer, Contact> contactMap) {
        String where = COL_CHAT_ID + " == " + chatID;
        ResultSet resultSet;
        try {
            resultSet = db.execSelectWhereInsecure(TABLE, where);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't get receiver from db", ex);
            return Collections.emptyList();
        }
        List<Member> members = new ArrayList<>();
        try {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                int contactID = resultSet.getInt(COL_CONTACT_ID);
                int r = resultSet.getInt(COL_ROLE);
                Role role = Role.values()[r];
                Contact c = contactMap.get(contactID);
                if (c == null) {
                    LOGGER.warning("can't find contact, ID:"+contactID);
                    continue;
                }

                members.add(new Member(id, c, role));
            }
            resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't get members", ex);
        }
        return members;
    }
}