package org.restcomm.connect.sms.smpp.dlr.provider;


import org.junit.Assert;
import org.junit.Test;
import org.restcomm.connect.dao.entities.SmsMessage.Status;
import org.restcomm.connect.sms.smpp.dlr.spi.DLRPayload;
import org.restcomm.connect.sms.smpp.dlr.spi.DlrParser;
import org.restcomm.connect.commons.dao.MessageError;

/**
 * @author mariafarooq
 *
 */
public class TelestaxDlrParserTest {

    @Test
    public void parseInvalidDLR() {
        final String pduMessage = "submit date:invaliddate stat:invalidStat";
        DlrParser parser = new TelestaxDlrParser();
        DLRPayload dlrMap = parser.parseMessage(pduMessage);
        Assert.assertNull(dlrMap.getId());
    }

    @Test
    public void parseDLRMessageTest() {
        final String pduMessage = "id:0000058049 sub:001 dlvrd:001 submit date:1805170144 done date:1805170144 stat:DELIVRD err:000 text:none                ";
        DlrParser parser = new TelestaxDlrParser();
        DLRPayload dlrMap = parser.parseMessage(pduMessage);
        Assert.assertEquals("0000058049", dlrMap.getId());
        Assert.assertEquals("001", dlrMap.getSub());
        Assert.assertEquals("001", dlrMap.getDlvrd());
        Assert.assertNull(dlrMap.getErr());
        Assert.assertEquals(Status.DELIVERED, dlrMap.getStat());
        Assert.assertEquals(2018, dlrMap.getDoneDate().getYear());
        Assert.assertEquals(2018, dlrMap.getSubmitDate().getYear());
    }

}
