package org.restcomm.connect.sms.smpp.dlr.spi;


public interface DlrParser {
    DLRPayload parseMessage(String message);
}