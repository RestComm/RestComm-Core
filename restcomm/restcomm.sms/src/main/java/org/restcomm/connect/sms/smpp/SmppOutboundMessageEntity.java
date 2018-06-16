package org.restcomm.connect.sms.smpp;

import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.smpp.parameter.TlvSet;

import com.cloudhopper.commons.charset.Charset;

public class SmppOutboundMessageEntity {


    private final String smppTo;
    private final String smppFrom;
    private final String smppContent;
    private final Charset smppEncoding;
    private final TlvSet tlvSet;
    private final Sid msgSid;


    public SmppOutboundMessageEntity(String smppTo, String smppFrom, String smppContent, Charset smppEncoding){
         this(smppTo, smppFrom, smppContent, smppEncoding, null, Sid.generate(Sid.Type.INVALID));
    }

    public SmppOutboundMessageEntity(String smppTo, String smppFrom, String smppContent, Charset smppEncoding, TlvSet tlvSet, Sid smsSid){
        this.smppTo = smppTo;
        this.smppFrom = smppFrom;
        this.smppContent = smppContent;
        this.smppEncoding = smppEncoding;
        this.tlvSet = tlvSet;
        this.msgSid = smsSid;
    }

    public final TlvSet getTlvSet(){
        return tlvSet;
    }



    public final String getSmppTo(){
        return smppTo;
    }

    public final String getSmppFrom(){
        return smppFrom;
    }
    public final String getSmppContent(){
        return smppContent;
    }
    public final Charset getSmppEncoding(){
        return smppEncoding;
    }

    public Sid getMessageSid() {
        return msgSid;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("SMPPOutboundMessage[From=")
            .append(smppFrom)
            .append(",To")
            .append(smppTo)
            .append(",Content=")
            .append(smppContent)
            .append(",Encoding=")
            .append(smppEncoding);
        if(tlvSet!=null){
        builder.append(",TlvSet=")
            .append(tlvSet.toString());
        }
        if(msgSid!=null){
        builder.append(",msgSid=")
            .append(msgSid.toString());
        }
        builder.append("]");
        return super.toString();
    }
}
