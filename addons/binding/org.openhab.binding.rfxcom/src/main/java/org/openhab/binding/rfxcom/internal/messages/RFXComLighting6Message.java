/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rfxcom.internal.messages;

import java.util.Arrays;
import java.util.List;

import org.eclipse.smarthome.core.library.items.ContactItem;
import org.eclipse.smarthome.core.library.items.NumberItem;
import org.eclipse.smarthome.core.library.items.SwitchItem;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;

import static org.openhab.binding.rfxcom.RFXComBindingConstants.*;

import org.openhab.binding.rfxcom.internal.exceptions.RFXComException;
import org.openhab.binding.rfxcom.internal.exceptions.RFXComUnsupportedValueException;

/**
 * RFXCOM data class for lighting6 message. See Blyss.
 *
 * @author Damien Servant - Initial contribution
 * @author Pauli Anttila
 */
public class RFXComLighting6Message extends RFXComBaseMessage {

    public enum SubType {
        BLYSS(0);

        private final int subType;

        SubType(int subType) {
            this.subType = subType;
        }

        public byte toByte() {
            return (byte) subType;
        }

        public static SubType fromByte(int input) throws RFXComUnsupportedValueException {
            for (SubType c : SubType.values()) {
                if (c.subType == input) {
                    return c;
                }
            }

            throw new RFXComUnsupportedValueException(SubType.class, input);
        }
    }

    public enum Commands {
        ON(0),
        OFF(1),
        GROUP_ON(2),
        GROUP_OFF(3);

        private final int command;

        Commands(int command) {
            this.command = command;
        }

        public byte toByte() {
            return (byte) command;
        }

        public static Commands fromByte(int input) throws RFXComUnsupportedValueException {
            for (Commands c : Commands.values()) {
                if (c.command == input) {
                    return c;
                }
            }

            throw new RFXComUnsupportedValueException(Commands.class, input);
        }
    }

    public SubType subType;
    public int sensorId;
    public char groupCode;
    public byte unitCode;
    public Commands command;
    public byte signalLevel;

    public RFXComLighting6Message() {
        packetType = PacketType.LIGHTING6;
    }

    public RFXComLighting6Message(byte[] data) throws RFXComException {

        encodeMessage(data);
    }

    @Override
    public String toString() {
        String str = "";

        str += super.toString();
        str += ", Sub type = " + subType;
        str += ", Device Id = " + getDeviceId();
        str += ", Command = " + command;
        str += ", Signal level = " + signalLevel;

        return str;
    }

    @Override
    public void encodeMessage(byte[] data) throws RFXComException {
        super.encodeMessage(data);

        subType = SubType.fromByte(super.subType);
        sensorId = (data[4] & 0xFF) << 8 | (data[5] & 0xFF);
        groupCode = (char) data[6];
        unitCode = data[7];
        command = Commands.fromByte(data[8]);

        signalLevel = (byte) ((data[11] & 0xF0) >> 4);
    }

    @Override
    public byte[] decodeMessage() {
        // Example data 0B 15 00 02 01 01 41 01 00 04 8E 00
        // 0B 15 00 02 01 01 41 01 01 04 8E 00

        byte[] data = new byte[12];

        data[0] = 0x0B;
        data[1] = RFXComBaseMessage.PacketType.LIGHTING6.toByte();
        data[2] = subType.toByte();
        data[3] = seqNbr;
        data[4] = (byte) ((sensorId >> 8) & 0xFF);
        data[5] = (byte) (sensorId & 0xFF);
        data[6] = (byte) groupCode;
        data[7] = unitCode;
        data[8] = command.toByte();
        data[9] = 0x00; // CmdSeqNbr1 - 0 to 4 - Useless for a Blyss Switch
        data[10] = 0x00; // CmdSeqNbr2 - 0 to 145 - Useless for a Blyss Switch
        data[11] = (byte) ((signalLevel & 0x0F) << 4);

        return data;
    }

    @Override
    public String getDeviceId() {
        return sensorId + ID_DELIMITER + groupCode + ID_DELIMITER + unitCode;
    }

    @Override
    public State convertToState(String channelId) throws RFXComException {

        switch (channelId) {
            case CHANNEL_SIGNAL_LEVEL:
                return new DecimalType(signalLevel);

            case CHANNEL_COMMAND:
                switch (command) {
                    case OFF:
                    case GROUP_OFF:
                        return OnOffType.OFF;

                    case ON:
                    case GROUP_ON:
                        return OnOffType.ON;

                    default:
                        throw new RFXComException("Can't convert " + command + " for " + channelId);
                }

            case CHANNEL_CONTACT:
                switch (command) {
                    case OFF:
                    case GROUP_OFF:
                        return OpenClosedType.CLOSED;

                    case ON:
                    case GROUP_ON:
                        return OpenClosedType.OPEN;

                    default:
                        throw new RFXComException("Can't convert " + command + " for " + channelId);
                }

            default:
                throw new RFXComException("Nothing relevant for " + channelId);
        }
    }

    @Override
    public void setSubType(Object subType) throws RFXComException {
        this.subType = ((SubType) subType);
    }

    @Override
    public void setDeviceId(String deviceId) throws RFXComException {
        String[] ids = deviceId.split("\\" + ID_DELIMITER);
        if (ids.length != 3) {
            throw new RFXComException("Invalid device id '" + deviceId + "'");
        }

        sensorId = Integer.parseInt(ids[0]);
        groupCode = ids[1].charAt(0);
        unitCode = Byte.parseByte(ids[2]);
    }

    @Override
    public void convertFromState(String channelId, Type type) throws RFXComException {

        switch (channelId) {
            case CHANNEL_COMMAND:
                if (type instanceof OnOffType) {
                    command = (type == OnOffType.ON ? Commands.ON : Commands.OFF);

                } else {
                    throw new RFXComException("Channel " + channelId + " does not accept " + type);
                }
                break;

            default:
                throw new RFXComException("Channel " + channelId + " is not relevant here");
        }

    }

    @Override
    public Object convertSubType(String subType) throws RFXComException {

        for (SubType s : SubType.values()) {
            if (s.toString().equals(subType)) {
                return s;
            }
        }

        try {
            return SubType.fromByte(Integer.parseInt(subType));
        } catch (NumberFormatException e) {
            throw new RFXComUnsupportedValueException(SubType.class, subType);
        }
    }
}
